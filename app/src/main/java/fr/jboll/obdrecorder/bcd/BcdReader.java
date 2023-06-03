package fr.jboll.obdrecorder.bcd;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

public class BcdReader {

	public Map<Integer, MessageType> read(File file) throws IOException {
		Map<Integer, MessageType> messages = new HashMap<>();
		MessageType currentMessageType = null;

		for (String line : Files.readAllLines(file.toPath())) {
			if (line.startsWith("BO_")) {
				final String[] words = line.split(" ");
				int messageId = Integer.parseInt(words[1]);
				final String messageName = words[2].replace(":", "");
				int bytesCounts = Integer.parseInt(words[3]);

				currentMessageType = new MessageType(messageId, messageName, bytesCounts, new ArrayList<>());
				messages.put(messageId, currentMessageType);
			}

			if (line.startsWith(" SG_")) {
				final String[] words = line.trim().split(" ");

				String signalId = words[1];

				boolean multiplexedKey = false;
				OptionalInt multiplexedValue = OptionalInt.empty();

				int readIndex = 3;

				if (words[2].equals("M")) {
					multiplexedKey = true;
					readIndex++;
				}
				if (words[2].startsWith("m")) {
					multiplexedValue = OptionalInt.of(Integer.parseInt(words[2].replace("m", "")));
					readIndex++;
				}


				final int startBit = Integer.parseInt(words[readIndex].split("\\|")[0]);
				final int bitLen = Integer.parseInt(words[readIndex].split("\\|")[1].split("@")[0]);
				final boolean signed = words[readIndex].endsWith("-");
				readIndex++;

				final double scale = Double.parseDouble(words[readIndex].split(",")[0].replace("(", ""));
				final double offset = Double.parseDouble(words[readIndex].split(",")[1].replace(")", ""));
				readIndex++;

				double min = Double.parseDouble(words[readIndex].split("\\|")[0].replace("[", ""));
				double max = Double.parseDouble(words[readIndex].split("\\|")[1].replace("]", ""));
				readIndex++;

				final String unit = words[readIndex].replace("\"", "");

				SignalType signalType = new SignalType(signalId, startBit, bitLen, signed, scale, offset, min, max, unit, multiplexedKey, multiplexedValue, new HashMap<>());
				currentMessageType.signalTypes().add(signalType);
			}

			// VAL_ 777 DAS_leftVeh2Type 4 "BICYCLE" 2 "CAR" 3 "MOTORCYCLE" 5 "PEDESTRIAN" 1 "TRUCK" 0 "UNKNOWN" ;
			if (line.startsWith("VAL_")) {
				final String[] words = line.trim().split(" ");
				final int messageId = Integer.parseInt(words[1]);
				final String signalId = words[2];

				SignalType foundSignal = null;
				final MessageType messageType = messages.get(messageId);
				for (SignalType signalType : messageType.signalTypes()) {
					if (signalType.signalId().equals(signalId)) {
						foundSignal = signalType;
						break;
					}
				}

				final String[] words2 = line.substring(line.indexOf(signalId) + signalId.length()).split("\"");
				for (int i = 0; i < words2.length - 1; i = i + 2) {
					final int key = Integer.parseInt(words2[i].trim());
					final String value = words2[i + 1].replaceAll("\"", "").trim();

					foundSignal.format().put(key, value);
				}
			}

		}

		return messages;
	}
}
