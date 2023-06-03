package fr.jboll.obdrecorder.decoder;

import java.util.OptionalDouble;

public class BitReader {
	byte[] data;
	int dataCount;

	public BitReader() {
	}

	public void setData(byte[] data, int dataCount) {
		this.data = data;
		this.dataCount = dataCount;
	}

	boolean readBit(int index) {
		final int dataIndex = index / 8;
		final int bitIndex = index % 8;

		if (dataIndex >= dataCount) {
			throw new IllegalStateException("buffer overflow");
		}

		final byte dataByte = this.data[dataIndex];

		// signed
		switch (bitIndex) {
			case 0:
				return (dataByte & 0b0000_0001) != 0;
			case 1:
				return (dataByte & 0b0000_0010) != 0;
			case 2:
				return (dataByte & 0b0000_0100) != 0;
			case 3:
				return (dataByte & 0b0000_1000) != 0;
			case 4:
				return (dataByte & 0b0001_0000) != 0;
			case 5:
				return (dataByte & 0b0010_0000) != 0;
			case 6:
				return (dataByte & 0b0100_0000) != 0;
			case 7:
				return (dataByte & 0b1000_0000) != 0;
		}
		throw new IllegalStateException("bitIndex shall not be > 7");
	}

	long readBits(int startBit, int bitLen) {
		long res = 0;

		for (int i = 0; i < bitLen; i++) {
			int currentBitIndex = startBit + i;
			final boolean bitValue = readBit(currentBitIndex);
			if (bitValue) {
				res |= 1L << i;
			}
		}

		return res;
	}


	public OptionalDouble readDouble(int startBit, int bitLen, boolean signed, double scale, double offset, double min, double max) {
		if (startBit + bitLen > dataCount * 8) {
			return OptionalDouble.empty();
		}

		long rawValue = readBits(startBit, bitLen);
		if (signed) {
			long msbMask = 1L << (bitLen - 1);
			rawValue = (rawValue ^ msbMask) - msbMask;
		}

		double physicalValue = (rawValue * scale) + offset;


		if ((min <= physicalValue && physicalValue <= max) || (max == min && min == 0)) {
			return OptionalDouble.of(physicalValue);

		} else {
			return OptionalDouble.empty();
		}
	}
}
