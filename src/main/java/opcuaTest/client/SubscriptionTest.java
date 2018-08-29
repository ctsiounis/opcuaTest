package opcuaTest.client;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MonitoringMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.MonitoredItemCreateRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.MonitoringParameters;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

public class SubscriptionTest implements Client {
	
	private OpcUaClient opcUaClient = null;
	private final Logger logger = LoggerFactory.getLogger(getClass());
	private final AtomicLong clientHandles = new AtomicLong(1L);
	ClientRunner runner;

	public static void main(String[] args) throws Exception {
		SubscriptionTest subTestClient = new SubscriptionTest();
		subTestClient.createNewOpcUaClient(subTestClient, 0);
		subTestClient.run();
	}

	public void setOpcUaClient(OpcUaClient opcUaClient) {
		this.opcUaClient = opcUaClient;
	}



	@Override
	public void run() throws Exception {
		// Check if opcUaClient and future have been initialized
		if (opcUaClient == null) {
			createNewOpcUaClient(this, 0);
		}
		
		// synchronous connect
		opcUaClient.connect().get();

		// Create subscription
		UaSubscription subscription = opcUaClient.getSubscriptionManager().createSubscription(1000.0).get();

		List<MonitoredItemCreateRequest> requests = new ArrayList<MonitoredItemCreateRequest>();
		ReadValueId readValueId;
		UInteger clientHandle;
		MonitoringParameters parameters;
		MonitoredItemCreateRequest request;
		
		for (int i = 0; i < 5; i++) {
			// Subscribe to a value
			readValueId = new ReadValueId(
					new NodeId(2, "TestFolder/TestSubfolder1/TestVariable_1_" + i),
					AttributeId.Value.uid(), 
					null, 
					QualifiedName.NULL_VALUE
			);
			
			// important: client handle must be unique per item
			clientHandle = uint(clientHandles.getAndIncrement());

			// Set monitoring parameters
			parameters = new MonitoringParameters(clientHandle, 1000.0, // sampling interval
					null, // filter, null means use default
					uint(10), // queue size
					true // discard oldest
			);

			// Form request to create MonitoringItem
			request = new MonitoredItemCreateRequest(
					readValueId, 
					MonitoringMode.Reporting,
					parameters
			);
			// Add the request to the array list of all requests
			requests.add(request);
		}
		// Set consumer for values received
		BiConsumer<UaMonitoredItem, Integer> onItemCreated = (item, id) -> item
				.setValueConsumer(this::onSubscriptionValue);

		// Create Monitored Items
		List<UaMonitoredItem> items = subscription
				.createMonitoredItems(TimestampsToReturn.Both, requests, onItemCreated).get();

		// Check returned status codes for created items
		for (UaMonitoredItem item : items) {
			if (item.getStatusCode().isGood()) {
				logger.info("item created for nodeId={}", item.getReadValueId().getNodeId());
			} else {
				logger.warn("failed to create item for nodeId={} (status={})", item.getReadValueId().getNodeId(),
						item.getStatusCode());
			}
		}

		// let the example run for 150 seconds then terminate
		System.out.println("Waiting for a change...");
		Thread.sleep(150000);
		
		//ClientRunner.closeOpcUaClient(opcUaClient);
	}

	private void onSubscriptionValue(UaMonitoredItem item, DataValue value) {
		logger.info("subscription value received: item={}, value={}", item.getReadValueId().getNodeId(),
				value.getValue());
	}

	@Override
	// Override to set proper endpoint
	public String getEndpointUrl() {
		return "opc.tcp://localhost:12686/test";
	}

	@Override
	public void createNewOpcUaClient(Client client, int endpointSelection) {
		runner = new ClientRunner(
				client.getSecurityPolicy(), 
				client.getEndpointUrl(), 
				client.getIdentityProvider(), 
				endpointSelection
		);
		try {
			opcUaClient = runner.createOpcUaClient();
		} catch (Throwable t) {
            logger.error("Error getting client: {}", t.getMessage(), t);

            try {
                Thread.sleep(1000);
                System.exit(0);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
	}

}
