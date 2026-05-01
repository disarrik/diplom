package observability.app.kafka

import observability.common.processor.LineageProcessor
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.common.errors.WakeupException
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

class LineageConsumerLoop(
    private val consumer: Consumer<ByteArray, ByteArray>,
    private val topic: String,
    private val processor: LineageProcessor,
) : Runnable {

    private val running = AtomicBoolean(true)

    fun stop() {
        running.set(false)
        consumer.wakeup()
    }

    override fun run() {
        consumer.subscribe(listOf(topic))
        try {
            while (running.get()) {
                val records = consumer.poll(Duration.ofSeconds(1))
                for (record in records) {
                    try {
                        val lineage = EventCodec.decodeLineage(record.value())
                        processor.process(lineage)
                    } catch (t: Throwable) {
                        System.err.println("bad lineage at offset ${record.offset()}: $t")
                    }
                }
                if (!records.isEmpty) {
                    consumer.commitAsync()
                }
            }
        } catch (_: WakeupException) {
            // expected during shutdown
        } finally {
            runCatching { consumer.commitSync() }
            consumer.close()
        }
    }
}
