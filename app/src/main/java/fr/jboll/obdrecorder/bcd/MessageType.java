package fr.jboll.obdrecorder.bcd;

import java.util.List;

public record MessageType(long messageId, String messageName, int bytesCounts, List<SignalType> signalTypes) {
}
