package observability.app.kafka

import observability.common.processor.IncidentProcessor
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.common.errors.WakeupException
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

class IncidentConsumerLoop(
    private val consumer: Consumer<ByteArray, ByteArray>,
    private val topic: String,
    private val processor: IncidentProcessor,
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
                        val incident = EventCodec.decodeIncident(record.value())
                        processor.process(incident)
                    } catch (t: Throwable) {
                        System.err.println("bad incident at offset ${record.offset()}: $t")
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
