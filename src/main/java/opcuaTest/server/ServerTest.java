package opcuaTest.server;

import java.io.File;
import java.security.Security;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.google.common.collect.ImmutableList;

import opcuaTest.namespaces.AnotherNamespace;
import opcuaTest.namespaces.TestNamespace;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.eclipse.milo.opcua.sdk.server.DiagnosticsContext;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.Session;
import org.eclipse.milo.opcua.sdk.server.api.AttributeHistoryManager.HistoryReadContext;
import org.eclipse.milo.opcua.sdk.server.api.Namespace;
import org.eclipse.milo.opcua.sdk.server.api.NodeManager.AddNodesContext;
import org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig;
import org.eclipse.milo.opcua.sdk.server.identity.CompositeValidator;
import org.eclipse.milo.opcua.sdk.server.identity.UsernameIdentityValidator;
import org.eclipse.milo.opcua.sdk.server.identity.X509IdentityValidator;
import org.eclipse.milo.opcua.sdk.server.services.AttributeHistoryServices;
import org.eclipse.milo.opcua.sdk.server.services.AttributeServices;
import org.eclipse.milo.opcua.sdk.server.services.ServiceAttributes;
import org.eclipse.milo.opcua.sdk.server.util.HostnameUtil;
import org.eclipse.milo.opcua.sdk.server.util.PendingHistoryRead;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.application.DefaultCertificateManager;
import org.eclipse.milo.opcua.stack.core.application.DirectoryCertificateValidator;
import org.eclipse.milo.opcua.stack.core.application.services.AttributeHistoryServiceSet;
import org.eclipse.milo.opcua.stack.core.application.services.NodeManagementServiceSet;
import org.eclipse.milo.opcua.stack.core.application.services.ServiceRequest;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.DiagnosticInfo;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.structured.AddNodesItem;
import org.eclipse.milo.opcua.stack.core.types.structured.AddNodesRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.AddNodesResponse;
import org.eclipse.milo.opcua.stack.core.types.structured.BuildInfo;
import org.eclipse.milo.opcua.stack.core.types.structured.HistoryReadDetails;
import org.eclipse.milo.opcua.stack.core.types.structured.HistoryReadRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.HistoryReadResponse;
import org.eclipse.milo.opcua.stack.core.types.structured.HistoryReadResult;
import org.eclipse.milo.opcua.stack.core.types.structured.HistoryReadValueId;
import org.eclipse.milo.opcua.stack.core.types.structured.ResponseHeader;
import org.eclipse.milo.opcua.stack.core.util.CertificateUtil;
import org.eclipse.milo.opcua.stack.core.util.CryptoRestrictions;
import org.eclipse.milo.opcua.stack.core.util.FutureUtils;
import org.slf4j.LoggerFactory;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static com.google.common.collect.Lists.newArrayListWithCapacity;
import static org.eclipse.milo.opcua.stack.core.util.ConversionUtil.a;
import static org.eclipse.milo.opcua.stack.core.util.ConversionUtil.l;
import static org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig.USER_TOKEN_POLICY_ANONYMOUS;
import static org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig.USER_TOKEN_POLICY_USERNAME;
import static org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig.USER_TOKEN_POLICY_X509;

public class ServerTest {
	
	static {
        CryptoRestrictions.remove();

        // Required for SecurityPolicy.Aes256_Sha256_RsaPss
        Security.addProvider(new BouncyCastleProvider());
    }
	
	private final OpcUaServer server;

	public static void main(String[] args) throws Exception {
		ServerTest server = new ServerTest();
		
		server.startup().get();
		
		final CompletableFuture<Void> future = new CompletableFuture<>();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> future.complete(null)));

        future.get();

	}

	public ServerTest() throws Exception{
		File securityTempDir = new File(System.getProperty("java.io.tmpdir"), "security");
        if (!securityTempDir.exists() && !securityTempDir.mkdirs()) {
            throw new Exception("unable to create security temp dir: " + securityTempDir);
        }
        LoggerFactory.getLogger(getClass()).info("security temp dir: {}", securityTempDir.getAbsolutePath());

        KeyStoreLoader loader = new KeyStoreLoader().load(securityTempDir);

        DefaultCertificateManager certificateManager = new DefaultCertificateManager(
            loader.getServerKeyPair(),
            loader.getServerCertificateChain()
        );

        File pkiDir = securityTempDir.toPath().resolve("pki").toFile();
        DirectoryCertificateValidator certificateValidator = new DirectoryCertificateValidator(pkiDir);
        LoggerFactory.getLogger(getClass()).info("pki dir: {}", pkiDir.getAbsolutePath());

        UsernameIdentityValidator identityValidator = new UsernameIdentityValidator(
            true,
            authChallenge -> {
                String username = authChallenge.getUsername();
                String password = authChallenge.getPassword();

                boolean userOk = "user".equals(username) && "password1".equals(password);
                boolean adminOk = "admin".equals(username) && "password2".equals(password);

                return userOk || adminOk;
            }
        );

        X509IdentityValidator x509IdentityValidator = new X509IdentityValidator(c -> true);

        List<String> bindAddresses = newArrayList();
        bindAddresses.add("0.0.0.0");

        List<String> endpointAddresses = newArrayList();
        endpointAddresses.add(HostnameUtil.getHostname());
        endpointAddresses.addAll(HostnameUtil.getHostnames("0.0.0.0"));

        // The configured application URI must match the one in the certificate(s)
        String applicationUri = certificateManager.getCertificates().stream()
            .findFirst()
            .map(certificate ->
                CertificateUtil.getSubjectAltNameField(certificate, CertificateUtil.SUBJECT_ALT_NAME_URI)
                    .map(Object::toString)
                    .orElseThrow(() -> new RuntimeException("certificate is missing the application URI")))
            .orElse("urn:ca:uwo:test:server:" + UUID.randomUUID());

        OpcUaServerConfig serverConfig = OpcUaServerConfig.builder()
            .setApplicationUri(applicationUri)
            .setApplicationName(LocalizedText.english("Test OPC UA Server"))
            .setBindPort(12686)
            .setBindAddresses(bindAddresses)
            .setEndpointAddresses(endpointAddresses)
            .setBuildInfo(
                new BuildInfo(
                    "urn:ca:uwo:test-server",
                    "ca.uwo",
                    "test server",
                    OpcUaServer.SDK_VERSION,
                    "", DateTime.now()))
            .setCertificateManager(certificateManager)
            .setCertificateValidator(certificateValidator)
            .setIdentityValidator(new CompositeValidator(identityValidator, x509IdentityValidator))
            .setProductUri("urn:ca:uwo:test-server")
            .setServerName("test")
            .setSecurityPolicies(
                EnumSet.of(
                    SecurityPolicy.None,
                    SecurityPolicy.Basic128Rsa15,
                    SecurityPolicy.Basic256,
                    SecurityPolicy.Basic256Sha256,
                    SecurityPolicy.Aes128_Sha256_RsaOaep,
                    SecurityPolicy.Aes256_Sha256_RsaPss))
            .setUserTokenPolicies(
                ImmutableList.of(
                    USER_TOKEN_POLICY_ANONYMOUS,
                    USER_TOKEN_POLICY_USERNAME,
                    USER_TOKEN_POLICY_X509))
            .build();

        server = new OpcUaServer(serverConfig);

        server.getNamespaceManager().registerAndAdd(
            TestNamespace.NAMESPACE_URI,
            idx -> new TestNamespace(server, idx));
        
        server.getNamespaceManager().registerAndAdd(
        		AnotherNamespace.NAMESPACE_URI, 
        		idx -> new AnotherNamespace(server, idx));
        
        
        //Adds ServiceSet to manage attribute history
        server.getServer().addServiceSet(new AttributeHistoryServiceSet() {

			@Override
			public void onHistoryRead(ServiceRequest<HistoryReadRequest, HistoryReadResponse> service)
					throws UaException {

		        HistoryReadRequest request = service.getRequest();

		        DiagnosticsContext<HistoryReadValueId> diagnosticsContext = new DiagnosticsContext<>();

		        List<HistoryReadValueId> nodesToRead = l(request.getNodesToRead());

		        if (nodesToRead.isEmpty()) {
		            service.setServiceFault(StatusCodes.Bad_NothingToDo);
		            return;
		        }

		        if (nodesToRead.size() > server.getConfig().getLimits().getMaxNodesPerRead().longValue()) {
		            service.setServiceFault(StatusCodes.Bad_TooManyOperations);
		            return;
		        }

		        if (request.getTimestampsToReturn() == null) {
		            service.setServiceFault(StatusCodes.Bad_TimestampsToReturnInvalid);
		            return;
		        }


		        List<PendingHistoryRead> pendingReads = newArrayListWithCapacity(nodesToRead.size());
		        List<CompletableFuture<HistoryReadResult>> futures = newArrayListWithCapacity(nodesToRead.size());

		        for (HistoryReadValueId id : nodesToRead) {
		            PendingHistoryRead pending = new PendingHistoryRead(id);

		            pendingReads.add(pending);
		            futures.add(pending.getFuture());
		        }

		        // Group PendingReads by namespace and call read for each.

		        Map<UShort, List<PendingHistoryRead>> byNamespace = pendingReads.stream()
		            .collect(groupingBy(pending -> pending.getInput().getNodeId().getNamespaceIndex()));

		        byNamespace.keySet().forEach(index -> {
		            List<PendingHistoryRead> pending = byNamespace.get(index);

		            CompletableFuture<List<HistoryReadResult>> future = new CompletableFuture<>();

		            HistoryReadContext context = new HistoryReadContext(
		                server, null, future, diagnosticsContext);

		            server.getExecutorService().execute(() -> {
		                Namespace namespace = server.getNamespaceManager().getNamespace(index);

		                List<HistoryReadValueId> readValueIds = pending.stream()
		                    .map(PendingHistoryRead::getInput)
		                    .collect(toList());

		                namespace.historyRead(
		                    context,
		                    (HistoryReadDetails) request.getHistoryReadDetails().decode(),
		                    request.getTimestampsToReturn(),
		                    readValueIds);
		            });

		            future.thenAccept(values -> {
		                for (int i = 0; i < values.size(); i++) {
		                    pending.get(i).getFuture().complete(values.get(i));
		                }
		            });
		        });

		        // When all PendingReads have been completed send a HistoryReadResponse with the values.

		        FutureUtils.sequence(futures).thenAcceptAsync(values -> {
		            ResponseHeader header = service.createResponseHeader();

		            DiagnosticInfo[] diagnosticInfos =
		                diagnosticsContext.getDiagnosticInfos(nodesToRead);

		            HistoryReadResponse response = new HistoryReadResponse(
		                header, a(values, HistoryReadResult.class), diagnosticInfos);

		            service.setResponse(response);
		        }, server.getExecutorService());
			}
        	
		});
        
        //Adds ServiceSet to manage node operations
        server.getServer().addServiceSet(new NodeManagementServiceSet() {

			@Override
			//Need to override from NodeManagementServiceSet to get this service
			public void onAddNodes(ServiceRequest<AddNodesRequest, AddNodesResponse> serviceRequest)
					throws UaException {
				List<AddNodesItem> nodesToAdd = Arrays.asList(serviceRequest.getRequest().getNodesToAdd());
				AddNodesContext context = new AddNodesContext(server, null, null);
				
				// Group by Namespace
				Map<UShort, List<AddNodesItem>> byNamespace = nodesToAdd.stream()
			            .collect(groupingBy(node -> node.getRequestedNewNodeId().getNamespaceIndex()));
				
				byNamespace.keySet().forEach(index -> 
					server.getNamespaceManager().getNamespace(index).addNode(context, byNamespace.get(index))
				);
				//UShort id = server.getNamespaceManager().getNamespaceTable().getIndex(TestNamespace.NAMESPACE_URI);
				
				//server.getNamespaceManager().getNamespace(id).addNode(context, nodesToAdd);
				
			}
        	
		});
        
        
        
	}
	
	public OpcUaServer getServer() {
        return server;
    }

    public CompletableFuture<OpcUaServer> startup() {
        return server.startup();
    }

    public CompletableFuture<OpcUaServer> shutdown() {
        return server.shutdown();
    }

}
