package mqtt.client;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class SimpleMqttClient {
	int qos = 2;
	String broker = "tcp://0.0.0.0:1883";
	String clientId = "opc-ua-linker";
	MqttClient client;
	
	public SimpleMqttClient() {
		try {
			client = new MqttClient(broker, clientId);
			client.connect();
		} catch (MqttException me) {
			me.printStackTrace();
		}
		
		
	}

	public void publish(String topic, String content) {
		try {
			MqttMessage message = new MqttMessage(content.getBytes());
			message.setQos(qos);
			client.publish(topic, message);
		} catch (MqttException me) {
			me.printStackTrace();
		}
	}
	
	public void close() {
		try {
			client.disconnect();
			client.close();
		} catch (MqttException e) {
			e.printStackTrace();
		}
		
	}
}
