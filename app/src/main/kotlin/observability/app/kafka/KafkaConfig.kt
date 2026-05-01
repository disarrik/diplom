package observability.app.kafka

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.ByteArraySerializer
import java.util.Properties

object KafkaConfig {
    val bootstrap: String
        get() = System.getenv("KAFKA_BOOTSTRAP_SERVERS")
            ?: error("KAFKA_BOOTSTRAP_SERVERS required for processor/importer modes")

    val lineageTopic: String = System.getenv("KAFKA_LINEAGE_TOPIC") ?: "observability.lineage"
    val incidentTopic: String = System.getenv("KAFKA_INCIDENT_TOPIC") ?: "observability.incidents"
    val consumerGroup: String = System.getenv("KAFKA_CONSUMER_GROUP") ?: "observability-processor"
    val clientId: String = System.getenv("KAFKA_CLIENT_ID") ?: "observability-app"

    fun producerProps(): Properties = Properties().apply {
        put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap)
        put(ProducerConfig.CLIENT_ID_CONFIG, clientId)
        put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer::class.java.name)
        put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer::class.java.name)
        put(ProducerConfig.ACKS_CONFIG, "all")
        put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true")
    }

    fun consumerProps(group: String = consumerGroup): Properties = Properties().apply {
        put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap)
        put(ConsumerConfig.GROUP_ID_CONFIG, group)
        put(ConsumerConfig.CLIENT_ID_CONFIG, "$clientId-$group")
        put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer::class.java.name)
        put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer::class.java.name)
        put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
        put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    }
}
