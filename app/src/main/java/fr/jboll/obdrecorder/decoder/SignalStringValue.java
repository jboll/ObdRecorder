package fr.jboll.obdrecorder.decoder;

import fr.jboll.obdrecorder.bcd.SignalType;

public record SignalStringValue(SignalType type, String value) {

}
