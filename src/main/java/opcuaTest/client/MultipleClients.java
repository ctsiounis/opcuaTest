package opcuaTest.client;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.identity.IdentityProvider;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MultipleClients {
	
	private final Logger logger = LoggerFactory.getLogger(getClass());
	OpcUaClient[] opcUaClients = new OpcUaClient[3];
	List<Client> clients = new ArrayList<Client>();
	List<CompletableFuture<Void>> futures = new ArrayList<CompletableFuture<Void>>();

	public static void main(String[] args) throws Exception {
		MultipleClients multiple = new MultipleClients();
		multiple.run();
	}
	
	public void run() throws Exception {
		ClientTest clientTest = new ClientTest();
		SubscriptionTest subTest;
		CustomDataTypeClient custom;
		
		OpcUaClient opcUaClient;
		
		// Create 3 OpcUaClients
		for (int i = 0; i < 3; i++) {
			opcUaClient = createOpcUaClient(
					clientTest.getSecurityPolicy(),
					clientTest.getEndpointUrl(),
					clientTest.getIdentityProvider(), 
					0
			);
			opcUaClients[i] = opcUaClient;
		}
		
		// Create 10 clients
		opcUaClient = opcUaClients[0];
		for (int i = 0; i < 4; i++) {
			clientTest = new ClientTest();
			clientTest.setOpcUaClient(opcUaClient);
			clients.add(clientTest);
		}
		
		opcUaClient = opcUaClients[1];
		for (int i = 0; i < 3; i++) {
			subTest = new SubscriptionTest();
			subTest.setOpcUaClient(opcUaClient);
			clients.add(subTest);
		}
		
		opcUaClient = opcUaClients[2];
		for (int i = 0; i < 3; i++) {
			custom = new CustomDataTypeClient();
			custom.setOpcUaClient(opcUaClient);
			clients.add(custom);
		}
		
		// Run all clients
		for (Client client : clients) {
			CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
				try {
					client.run();
				} catch (Exception e) {
					logger.error("Error when running client: {}", e.getMessage());
				}
			});
			futures.add(future);
		}
		
		CompletableFuture<Void> allClients = CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()]));
		
		while (!allClients.isDone()) {
			Thread.sleep(1000);
		}
		
		logger.info("All clients finished!!!");
		
		// Close all OpcUaClients
		for (OpcUaClient client : opcUaClients ) {
			ClientRunner.closeOpcUaClient(client);
		}
	}
	
	public OpcUaClient createOpcUaClient(
			SecurityPolicy securityPolicy,
			String endpointUrl,
			IdentityProvider identityProvider, 
			int endpointSelection) {
		
		OpcUaClient opcUaClient = null;
		ClientRunner runner = new ClientRunner(securityPolicy, endpointUrl, identityProvider, endpointSelection);
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
		
		return opcUaClient;
	}

}
