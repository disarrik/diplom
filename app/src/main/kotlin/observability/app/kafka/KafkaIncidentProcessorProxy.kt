package observability.app.kafka

import observability.common.model.DataIncident
import observability.common.processor.IncidentProcessor
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord

class KafkaIncidentProcessorProxy(
    private val producer: Producer<ByteArray, ByteArray>,
    private val topic: String,
) : IncidentProcessor {

    override fun process(dataIncident: DataIncident) {
        val key = dataIncident.id.toString().toByteArray(Charsets.UTF_8)
        val value = EventCodec.encodeIncident(dataIncident)
        producer.send(ProducerRecord(topic, key, value)) { _, ex ->
            if (ex != null) {
                System.err.println("kafka publish to $topic failed: $ex")
            }
        }
    }
}
