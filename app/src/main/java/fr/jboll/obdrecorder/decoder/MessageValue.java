package fr.jboll.obdrecorder.decoder;

import fr.jboll.obdrecorder.bcd.MessageType;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public record MessageValue(long currentDateTime, MessageType type, List<SignalStringValue> stringValues, List<SignalDoubleValue> doubleValues,
						   byte[] data) {

	public SignalDoubleValue signalDouble(String signalId) {
		for (SignalDoubleValue doubleValue : doubleValues) {
			if (doubleValue.type().signalId().equals(signalId)) {
				return doubleValue;
			}
		}
		throw new IllegalArgumentException("not found");
	}

	public SignalStringValue signalString(String signalId) {
		for (SignalStringValue stringValue : stringValues) {
			if (stringValue.type().signalId().equals(signalId)) {
				return stringValue;
			}
		}
		throw new IllegalArgumentException("not found");
	}

	public int signalIntValue(String signalId) {
		for (SignalDoubleValue doubleValue : doubleValues) {
			if (doubleValue.type().signalId().equals(signalId)) {
				return (int) doubleValue.value();
			}
		}
		throw new IllegalArgumentException("not found");
	}

	public double signalDoubleValue(String signalId) {
		for (SignalDoubleValue doubleValue : doubleValues) {
			if (doubleValue.type().signalId().equals(signalId)) {
				return doubleValue.value();
			}
		}
		throw new IllegalArgumentException("not found");
	}

	public String signalStringValue(String signalId) {
		for (SignalStringValue stringValue : stringValues) {
			if (stringValue.type().signalId().equals(signalId)) {
				return stringValue.value();
			}
		}
		throw new IllegalArgumentException("not found");
	}

	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		b.append(new SimpleDateFormat("EEEE dd MMM yyyy HH:mm:ss ").format(new Date(currentDateTime)));
		b.append(type.messageName());
		b.append(" ");
		b.append(type.messageId());
		b.append(" {\n");
		for (SignalStringValue value : stringValues) {
			b.append("\t");
			b.append(value.type().signalId());
			b.append(" : ");
			b.append(value.value());
			b.append("\n");
		}
		for (SignalDoubleValue value : doubleValues) {
			b.append("\t");
			b.append(value);
			b.append("\n");
		}
		b.append("}");
		return b.toString();
	}
}
