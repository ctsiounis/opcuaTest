package opcuaTest.client;

import java.util.concurrent.CompletableFuture;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.identity.AnonymousProvider;
import org.eclipse.milo.opcua.sdk.client.api.identity.IdentityProvider;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;

public interface Client {
	
	void createNewOpcUaClient(Client client, int endpointSelection);
	
	void setOpcUaClient(OpcUaClient opcUaClient);
	
	void run() throws Exception;
	
	default String getEndpointUrl() {
        return "opc.tcp://localhost:12686/example";
    }

    default SecurityPolicy getSecurityPolicy() {
        return SecurityPolicy.None;
    }

    default IdentityProvider getIdentityProvider() {
        return new AnonymousProvider();
    }
}
