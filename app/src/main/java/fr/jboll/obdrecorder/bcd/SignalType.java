package fr.jboll.obdrecorder.bcd;

import java.util.Map;
import java.util.OptionalInt;

public record SignalType(String signalId, int startBit, int bitLen, boolean signed, double scale, double offset, double min,
				  double max, String unit, boolean multiplexedKey, OptionalInt multiplexedValue,
				  Map<Integer, String> format) {

}
