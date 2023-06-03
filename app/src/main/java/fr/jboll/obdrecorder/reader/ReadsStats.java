package fr.jboll.obdrecorder.reader;

public record ReadsStats(int messageCount, int messagesDecoded, int messagesFiltered, long recordDurationSec,
						 double messagesDecodedBySec, double messagesBySec, long decodingDurationSec) {
}
