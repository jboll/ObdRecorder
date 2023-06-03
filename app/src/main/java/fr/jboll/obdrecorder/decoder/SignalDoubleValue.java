package fr.jboll.obdrecorder.decoder;

import fr.jboll.obdrecorder.bcd.SignalType;

public record SignalDoubleValue(SignalType type, double value) {

	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		b.append(type().signalId());
		b.append(" : ");

		if (type.scale() >= 1) {
			// integer
			if ((int) value != value) {
				throw new IllegalStateException("loss precision");
			}
			b.append((int)value());
		}
		else{
			// double
			b.append(value);
		}


		b.append(" ");
		b.append(type().unit());
		return b.toString();
	}

}
