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
		for (int i = 0; i < 4; i++) {
			opcUaClient = opcUaClients[0];
			clientTest = new ClientTest();
			clientTest.setOpcUaClient(opcUaClient);
			clients.add(clientTest);
		}
		
		for (int i = 0; i < 3; i++) {
			opcUaClient = opcUaClients[1];
			subTest = new SubscriptionTest();
			subTest.setOpcUaClient(opcUaClient);
			clients.add(subTest);
		}
		
		for (int i = 0; i < 3; i++) {
			opcUaClient = opcUaClients[2];
			custom = new CustomDataTypeClient();
			custom.setOpcUaClient(opcUaClient);
			clients.add(custom);
		}
		
		// Run all clients
		for (Client client : clients) {
			client.run();
		}
		
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
