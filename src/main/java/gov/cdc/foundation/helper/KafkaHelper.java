package gov.cdc.foundation.helper;

import java.util.Properties;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.KafkaException;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class KafkaHelper {

	private static final Logger logger = Logger.getLogger(KafkaHelper.class);

	private static KafkaHelper instance;

	private String kafkaBrokers;
	private String kafkaAcknowledgments;
	private int kafkaRetries;
	private String kafkaKeySerializer;
	private String kafkaValueSerializer;

	public KafkaHelper(@Value("${kafka.brokers}") String kafkaBrokers, @Value("${kafka.acknowledgments}") String kafkaAcknowledgments, @Value("${kafka.retries}") int kafkaRetries, @Value("${kafka.key.serializer}") String kafkaKeySerializer, @Value("${kafka.value.serializer}") String kafkaValueSerializer) {
		this.kafkaBrokers = kafkaBrokers;
		this.kafkaAcknowledgments = kafkaAcknowledgments;
		this.kafkaRetries = kafkaRetries;
		this.kafkaKeySerializer = kafkaKeySerializer;
		this.kafkaValueSerializer = kafkaValueSerializer;
		instance = this;
	}

	public static KafkaHelper getInstance() {
		return instance;
	}

	public void sendMessage(String data, String topic) {
		Properties props = new Properties();
		props.put("bootstrap.servers", kafkaBrokers);
		props.put("acks", kafkaAcknowledgments);
		props.put("retries", kafkaRetries);

		props.put("key.serializer", kafkaKeySerializer);
		props.put("value.serializer", kafkaValueSerializer);

		Producer<String, String> producer = new KafkaProducer<String, String>(props);

		logger.debug("Sending message to: " + topic);
		producer.send(new ProducerRecord<String, String>(topic, "message", data), new Callback() {
			@Override
			public void onCompletion(RecordMetadata metadata, Exception e) {
				if (e != null) {
					throw new KafkaException("Kafka issue", e);
				}
			}
		});
		logger.debug("... Sent!");
		producer.close();
	}

}
