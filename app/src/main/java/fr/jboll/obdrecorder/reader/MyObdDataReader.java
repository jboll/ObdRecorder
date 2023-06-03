package fr.jboll.obdrecorder.reader;

import fr.jboll.obdrecorder.decoder.MessageDecoder;
import fr.jboll.obdrecorder.decoder.MessageValue;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class MyObdDataReader  {
	record Aggregator(int messageId, Duration duration, Function<List<MessageValue>, MessageValue> aggregator, List<MessageValue> pendingValues, AtomicLong lastAggregationInstant){}

	MessageDecoder messageDecoder;
	private IntPredicate messageIdFilter;
	private Consumer<MessageValue> listener;
	private int messageCount = 0;
	private int messagesDecoded = 0;
	private int messagesFiltered = 0;
	private ReadsStats readsStats = null;
	private byte[] fileContent = null;
	private final int version = 0;
	private final List<Aggregator> aggregators = new ArrayList<>();


	public void setMessageAggregator(int messageId, Duration duration, Function<List<MessageValue>, MessageValue> aggregator) {
		aggregators.add(new Aggregator(messageId, duration, aggregator, new ArrayList<>(), new AtomicLong()));
	}

	public void setMessageIdFilter(IntPredicate messageIdFilter) {
		this.messageIdFilter = messageIdFilter;
	}

	public void setDbcFile(String dbcName) throws IOException {
		setDbcFile(new File(ClassLoader.getSystemClassLoader().getResource(dbcName).getFile()));
	}

	public void setDbcFile(File dbcFile) throws IOException {
		this.messageDecoder = new MessageDecoder(dbcFile);
	}

	public void setDataFile(String dataFileName) throws IOException {
		setDataFile(new File(ClassLoader.getSystemClassLoader().getResource(dataFileName).getFile()));
	}

	public void setDataFile(File dataFile) throws IOException {
		if (dataFile.getName().endsWith(".zip")) {
			try (ZipFile zipFile = new ZipFile(dataFile)) {
				final ZipEntry zipEntry = zipFile.entries().nextElement();
				this.fileContent = zipFile.getInputStream(zipEntry).readAllBytes();
			}
		}
		else {
			this.fileContent = Files.readAllBytes(dataFile.toPath());
		}

	}

	public Iterable<MessageValue> readMyObdFile() throws IOException {
		DataInputStream input = new DataInputStream(new ByteArrayInputStream(fileContent));

		// reset stats
		readsStats = null;
		messageCount = 0;
		messagesDecoded = 0;
		messagesFiltered = 0;

		// header
		final String dataFormat = input.readLine();
		if (dataFormat.equals("data format : Time (4 bytes), id (3 bytes), data len (1 bytes), data (0-8 bytes)")) {
			return readMyObdFileV0(input);
		}
		else if (dataFormat.equals("data format : id (3 bytes), data len (1 bytes), data (0-8 bytes)")) {
			return readMyObdFileV1(input);
		}
		else if (dataFormat.equals("data format : time (4bytes), id (1 byte {0=Unknown, 1=306, 2=612, 3=850, 4=1025, 5=792}), data len (1 byte), data (0-8 bytes)")) {
			return readMyObdFileV2(input);
		}
		else if (dataFormat.equals("data format : time (4bytes), id (1 byte {0=Unknown, 1=306, 2=612, 3=792, 4=826, 5=850, 6=1025}), data len (1 byte), data (0-8 bytes)")) {
			return readMyObdFileV3(input);
		}
		else if (dataFormat.equals("data format : time (4bytes), id (1 byte {0=Unknown, 1=818, 2=612, 3=792, 4=826, 5=850, 6=1025}), data len (1 byte), data (0-8 bytes)")) {
			return readMyObdFileV4(input);
		}
		else {
			throw new IllegalStateException("don't know how to decode : " + dataFormat);
		}
	}

	public ReadsStats getReadsStats() {
		return readsStats;
	}

	private abstract class MessageIterator implements Iterator<MessageValue> {
		boolean ended = false;
		final byte[] buffer = new byte[11];
		MessageValue nextValue = null;
		DataInputStream input;
		int messageId;
		int uptime = 0;
		byte[] data;
		byte len = 0;
		long firstMessageStart = 0;
		long lastMessageStart = 0;
		long startTime = System.nanoTime();

		public MessageIterator(DataInputStream input) {
			this.input = input;
		}

		@Override
		public boolean hasNext() {
			if (ended) {
				return false;
			} else {
				try {

					while (nextValue == null && input.available() > 0) {
						readNextMessage();

						final Optional<MessageValue> optionalValue = decodeMessage(messageId, buffer, len, uptime);
						optionalValue.ifPresent(messageValue -> nextValue = messageValue);
					}

					if (nextValue != null && firstMessageStart == 0) {
						firstMessageStart = nextValue.currentDateTime();
					}

					if (nextValue == null) {
						// EOF
						long endTime = System.nanoTime();
						long decodingDurationSec = TimeUnit.NANOSECONDS.toSeconds(endTime - startTime);
						long recordDurationSec = TimeUnit.MILLISECONDS.toSeconds(lastMessageStart - firstMessageStart);
						double messagesDecodedBySec = (double) messagesDecoded / recordDurationSec;
						double messagesBySec = (double) messageCount / recordDurationSec;

						readsStats = new ReadsStats(messageCount, messagesDecoded, messagesFiltered, recordDurationSec, messagesDecodedBySec, messagesBySec, decodingDurationSec);
						ended = true;
						return false;
					}
					else {
						lastMessageStart = nextValue.currentDateTime();
						return true;
					}
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		}

		protected abstract void readNextMessage() throws IOException;

		@Override
		public MessageValue next() {
			MessageValue returnValue = nextValue;
			nextValue = null;
			return returnValue;
		}
	}


	private Iterable<MessageValue> readMyObdFileV0(DataInputStream d) {
		return () -> new MessageIterator(d) {
			@Override
			protected void readNextMessage() throws IOException {
				input.readInt();
				messageId = input.readInt();
				len = input.readByte();
				input.readNBytes(buffer, 0, len);
			}
		};
	}


	private Iterable<MessageValue> readMyObdFileV1(DataInputStream d) {
		return () -> new MessageIterator(d) {
			@Override
			protected void readNextMessage() throws IOException {
				// id (3 bytes), bytes len (1 byte), data (0-8 bytes)
				messageId = (input.readByte() << 16 & 0x00FF0000) | (input.readByte() << 8 & 0x0000FF00) | (input.readByte() & 0x000000FF);

				len = input.readByte();
				input.readNBytes(buffer, 0, len);
			}
		};
	}

	private Iterable<MessageValue> readMyObdFileV2(DataInputStream d) {
		return () -> new MessageIterator(d) {
			@Override
			protected void readNextMessage() throws IOException {
				// data format : time (4bytes), id (1 byte {0=Unknown, 1=306, 2=612, 3=826, 4=850, 5=1025, 6=792}), data len (1 byte), data (0-8 bytes)
				uptime = input.readInt();
				final byte id = input.readByte();
				switch (id) {
					case 0 : throw new IllegalStateException("unknown message");
					case 1 : messageId = 306; break;
					case 2 : messageId = 612; break;
					case 3 : messageId = 826; break;
					case 4 : messageId = 850; break;
					case 5 : messageId = 1025; break;
					case 6 : messageId = 792; break;
					default:throw new IllegalStateException("unknown message <"+id+">");
				}

				len = input.readByte();
				if (len > 8) {
					System.err.println("here");
				}
				input.readNBytes(buffer, 0, len);
			}
		};
	}

	private Iterable<MessageValue> readMyObdFileV3(DataInputStream d) {
		return () -> new MessageIterator(d) {
			@Override
			protected void readNextMessage() throws IOException {
				// data format : time (4bytes), id (1 byte {0=Unknown, 1=306, 2=612, 3=792, 4=826, 5=850, 6=1025}), data len (1 byte), data (0-8 bytes)
				uptime = input.readInt();
				final byte id = input.readByte();
				switch (id) {
					case 1 : messageId = 306; break;
					case 2 : messageId = 612; break;
					case 3 : messageId = 792; break;
					case 4 : messageId = 826; break;
					case 5 : messageId = 850; break;
					case 6 : messageId = 1025; break;
					default: {
						System.err.println("unknown message <"+id+">");
						len = input.readByte();
						input.readNBytes(buffer, 0, len);
						readNextMessage();
					}
				}

				len = input.readByte();
				input.readNBytes(buffer, 0, len);
			}
		};
	}

	private Iterable<MessageValue> readMyObdFileV4(DataInputStream d) {
		return () -> new MessageIterator(d) {
			@Override
			protected void readNextMessage() throws IOException {
				// data format : time (4bytes), id (1 byte {0=Unknown, 1=818, 2=612, 3=792, 4=826, 5=850, 6=1025}), data len (1 byte), data (0-8 bytes)
				uptime = input.readInt();
				final byte id = input.readByte();
				switch (id) {
					case 1 : messageId = 818; break;
					case 2 : messageId = 612; break;
					case 3 : messageId = 792; break;
					case 4 : messageId = 826; break;
					case 5 : messageId = 850; break;
					case 6 : messageId = 1025; break;
					default: {
						System.err.println("unknown message <"+id+">");
						len = input.readByte();
						input.readNBytes(buffer, 0, len);
						readNextMessage();
					}
				}

				len = input.readByte();
				input.readNBytes(buffer, 0, len);
			}
		};
	}

	private Optional<MessageValue> decodeMessage(int messageId, byte[] data, byte len, int uptime) {
		messageCount++;

		if (messageId == 0x318 || this.messageIdFilter == null || this.messageIdFilter.test(messageId)) {
			final Optional<MessageValue> optionalValue = messageDecoder.decodeMessage(messageId, data, len, uptime);

			optionalValue.ifPresent(messageValue -> {

				// special case : the date & time
				if (messageId == 0x318) {
					ZonedDateTime z = ZonedDateTime.of(
							messageValue.signalIntValue("UTCyear318"),
							messageValue.signalIntValue("UTCmonth318"),
							messageValue.signalIntValue("UTCday318"),
							messageValue.signalIntValue("UTChour318"),
							messageValue.signalIntValue("UTCminutes318"),
							messageValue.signalIntValue("UTCseconds318"),
							0,
							ZoneId.of("GMT"));
					long currentDateTime = z.withZoneSameInstant(ZoneId.systemDefault()).toInstant().toEpochMilli();

					messageDecoder.synchronizeDateAndUptime(currentDateTime, uptime);
				}
				else {
					messagesDecoded++;
				}
			});

			if (messageId == 0x318 && this.messageIdFilter != null && !this.messageIdFilter.test(messageId)) {
				return Optional.empty();
			}

			if (optionalValue.isPresent() && optionalValue.get().currentDateTime() == 0) {
				return Optional.empty();
			}

			if (optionalValue.isPresent()) {
				for (Aggregator aggregator : aggregators) {
					if (aggregator.messageId == messageId) {

						MessageValue result = null;

						long duration = optionalValue.get().currentDateTime() - aggregator.lastAggregationInstant.get();
						if (duration >= aggregator.duration.toMillis()) {
							if (aggregator.lastAggregationInstant.get() != 0) {
								result = aggregator.aggregator.apply(aggregator.pendingValues);
							}
							aggregator.lastAggregationInstant.set(optionalValue.get().currentDateTime());
							aggregator.pendingValues.clear();
						}

						aggregator.pendingValues().add(optionalValue.get());

						return Optional.ofNullable(result);
					}
				}
			}

			return optionalValue;
		} else {
			messagesFiltered++;
		}


		return Optional.empty();
	}

}
