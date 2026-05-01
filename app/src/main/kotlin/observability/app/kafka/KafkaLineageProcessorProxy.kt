package observability.app.kafka

import observability.common.model.Lineage
import observability.common.processor.LineageProcessor
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord

class KafkaLineageProcessorProxy(
    private val producer: Producer<ByteArray, ByteArray>,
    private val topic: String,
) : LineageProcessor {

    override fun process(lineage: Lineage) {
        val key = lineage.id.toString().toByteArray(Charsets.UTF_8)
        val value = EventCodec.encodeLineage(lineage)
        producer.send(ProducerRecord(topic, key, value)) { _, ex ->
            if (ex != null) {
                System.err.println("kafka publish to $topic failed: $ex")
            }
        }
    }
}
