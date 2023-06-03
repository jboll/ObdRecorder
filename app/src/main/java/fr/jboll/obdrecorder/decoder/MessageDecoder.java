package fr.jboll.obdrecorder.decoder;


import fr.jboll.obdrecorder.bcd.BcdReader;
import fr.jboll.obdrecorder.bcd.MessageType;
import fr.jboll.obdrecorder.bcd.SignalType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

public class MessageDecoder {

	final Map<Integer, MessageType> messageTypes;
	final BitReader bitReader = new BitReader();
	private long currentDateTime;
	private long uptimeAtCurrentDateTime;


	public MessageDecoder(File dbcFile) throws IOException {
		final BcdReader bcdReader = new BcdReader();
		this.messageTypes = bcdReader.read(dbcFile);
	}

	public Optional<MessageValue> decodeMessage(int messageId, byte[] data, byte len, int uptime) {
		final MessageType messageType = messageTypes.get(messageId);
		if (messageType != null) {

			bitReader.setData(data, len);

			List<SignalDoubleValue> doubleValues = new ArrayList<>();
			List<SignalStringValue> stringValues = new ArrayList<>();

			SignalType multiplexedSignal = null;
			int multiplexValue = -1;

			for (SignalType signalType : messageType.signalTypes()) {
				if (signalType.multiplexedKey()) {
					multiplexedSignal = signalType;
					break;
				}
			}

			if (multiplexedSignal != null) {
				final OptionalDouble optionalDouble = bitReader.readDouble(
						multiplexedSignal.startBit(), multiplexedSignal.bitLen(),
						false,
						multiplexedSignal.scale(), multiplexedSignal.offset(),
						multiplexedSignal.min(), multiplexedSignal.max());

				if (optionalDouble.isEmpty()) {
					// a signal has not been decoded properly, trace it
					// System.err.println("Signal " + multiplexedSignal + " value can not be read from message " + Arrays.toString(data));
					return Optional.empty();
				}
				else {
					final int value = (int)optionalDouble.getAsDouble();
					final String formattedValue = multiplexedSignal.format().getOrDefault(value, String.valueOf(value));
					multiplexValue = value;
					stringValues.add(new SignalStringValue(multiplexedSignal, formattedValue));
				}
			}

			for (SignalType signalType : messageType.signalTypes()) {
				if (!signalType.multiplexedKey() && (signalType.multiplexedValue().isEmpty() || multiplexValue == signalType.multiplexedValue().getAsInt())) {
					final OptionalDouble optionalDouble = bitReader.readDouble(signalType.startBit(), signalType.bitLen(), signalType.signed(), signalType.scale(), signalType.offset(), signalType.min(), signalType.max());

					if (optionalDouble.isEmpty()) {
						// a signal has not been decoded properly, trace it
						// System.err.println("Signal " + signalType.signalId() + " value of message " + messageId + " can not be read from message data : " + Arrays.toString(data));
						return Optional.empty();
					}
					else {
						final double value = optionalDouble.getAsDouble();
						if (signalType.format().isEmpty() || !signalType.format().containsKey((int) value)) {
							doubleValues.add(new SignalDoubleValue(signalType, value));
						}
						else {
							stringValues.add(new SignalStringValue(signalType, signalType.format().getOrDefault((int) value, String.valueOf(value))));
						}
					}
				}
			}


			long now = this.currentDateTime;
			if (this.currentDateTime > 0 && uptime > 0) {
				final long deltaMs = uptime - this.uptimeAtCurrentDateTime;
				now += deltaMs;
			}
			return Optional.of(new MessageValue(now, messageType, stringValues, doubleValues, Arrays.copyOfRange(data, 0, len)));
		}

		return Optional.empty();
	}

	public void synchronizeDateAndUptime(long currentDateTime, int uptime) {
		// uptime is recorded
		if (uptime > 0) {
			if (this.currentDateTime == 0) {
				this.currentDateTime = currentDateTime;
				this.uptimeAtCurrentDateTime = uptime;
			}
		}
		else {
			// no uptime
			this.currentDateTime = currentDateTime;
		}

	}
}
