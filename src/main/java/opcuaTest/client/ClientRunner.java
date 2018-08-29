package opcuaTest.client;

import java.io.File;
import java.security.Security;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfig;
import org.eclipse.milo.opcua.sdk.client.api.identity.IdentityProvider;
import org.eclipse.milo.opcua.stack.client.UaTcpStackClient;
import org.eclipse.milo.opcua.stack.core.Stack;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.eclipse.milo.opcua.stack.core.util.CryptoRestrictions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

public class ClientRunner {

	static {
		CryptoRestrictions.remove();

		// Required for SecurityPolicy.Aes256_Sha256_RsaPss
		Security.addProvider(new BouncyCastleProvider());
	}

	private final Logger logger = LoggerFactory.getLogger(getClass());

	//private final Client client;
	private SecurityPolicy securityPolicy;
	private String endpointUrl;
	private IdentityProvider identityProvider;
	private int endpointSelection;

	public ClientRunner(SecurityPolicy securityPolicy, String endpointUrl, IdentityProvider identityProvider, int endpointSelection) {
		this.securityPolicy = securityPolicy;
		this.endpointUrl = endpointUrl;
		this.identityProvider = identityProvider;
		this.endpointSelection = endpointSelection;
	}

	public OpcUaClient createOpcUaClient() throws Exception {
		File securityTempDir = new File(System.getProperty("java.io.tmpdir"), "security");
		if (!securityTempDir.exists() && !securityTempDir.mkdirs()) {
			throw new Exception("unable to create security dir: " + securityTempDir);
		}
		LoggerFactory.getLogger(getClass()).info("security temp dir: {}", securityTempDir.getAbsolutePath());

		KeyStoreLoaderClient loader = new KeyStoreLoaderClient().load(securityTempDir);

		//SecurityPolicy securityPolicy = client.getSecurityPolicy();

		EndpointDescription[] endpoints;

		try {
			endpoints = UaTcpStackClient.getEndpoints(endpointUrl).get();
		} catch (Throwable ex) {
			// try the explicit discovery endpoint as well
			String discoveryUrl = endpointUrl + "/discovery";
			logger.info("Trying explicit discovery URL: {}", discoveryUrl);
			endpoints = UaTcpStackClient.getEndpoints(discoveryUrl).get();
		}

		EndpointDescription endpoint = Arrays.stream(endpoints)
				.filter(e -> e.getSecurityPolicyUri().equals(securityPolicy.getSecurityPolicyUri()))
				// Choosing which endpoint is used
				.skip(endpointSelection).findFirst().orElseThrow(() -> new Exception("no desired endpoints returned"));

		logger.info("Using endpoint: {} [{}]", endpoint.getEndpointUrl(), securityPolicy);

		OpcUaClientConfig config = OpcUaClientConfig.builder()
				.setApplicationName(LocalizedText.english("eclipse milo opc-ua client"))
				.setApplicationUri("urn:eclipse:milo:examples:client").setCertificate(loader.getClientCertificate())
				.setKeyPair(loader.getClientKeyPair()).setEndpoint(endpoint)
				.setIdentityProvider(identityProvider).setRequestTimeout(uint(5000)).build();

		return new OpcUaClient(config);
	}

	public static void closeOpcUaClient(OpcUaClient opcUaClient) {

			try {
				opcUaClient.disconnect().get();
				Stack.releaseSharedResources();
			} catch (InterruptedException | ExecutionException e) {
				System.out.println("Error disconnecting:"+e.getMessage()+ e);
			}

			try {
				Thread.sleep(1000);
				//System.exit(0);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

	}

}
