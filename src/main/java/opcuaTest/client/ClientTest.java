package opcuaTest.client;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.eclipse.milo.examples.server.types.CustomDataType;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.nodes.Node;
import org.eclipse.milo.opcua.sdk.client.api.nodes.VariableNode;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.OpcUaBinaryDataTypeDictionary;
import org.eclipse.milo.opcua.stack.core.types.OpcUaDataTypeManager;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExtensionObject;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.AddNodesItem;
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodResult;
import org.eclipse.milo.opcua.stack.core.types.structured.HistoryData;
import org.eclipse.milo.opcua.stack.core.types.structured.HistoryReadDetails;
import org.eclipse.milo.opcua.stack.core.types.structured.HistoryReadResponse;
import org.eclipse.milo.opcua.stack.core.types.structured.HistoryReadResult;
import org.eclipse.milo.opcua.stack.core.types.structured.HistoryReadValueId;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadRawModifiedDetails;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.eclipse.milo.opcua.stack.core.util.ConversionUtil.l;

public class ClientTest implements Client {
	
	private final Logger logger = LoggerFactory.getLogger(getClass());
	private OpcUaClient opcUaClient = null;
	private ClientRunner runner;

	public static void main(String[] args) throws Exception {
		ClientTest clientTest = new ClientTest();
		clientTest.createNewOpcUaClient(clientTest, 0);
		clientTest.run();
	}

	public void setOpcUaClient(OpcUaClient opcUaClient) {
		this.opcUaClient = opcUaClient;
	}



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



	public void run() throws Exception {
		// Check if opcUaClient and future have been initialized
		if (opcUaClient == null) {
			createNewOpcUaClient(this, 0);
		}
		
		// synchronous connect
		opcUaClient.connect().get();

		//Write values in already existing folder
		writeValues();
		
		//Read values in already existing folder
		readValues();
		
		//Add nodes
		addNodes();
		
		// browse nodes
		browseNode("", Identifiers.ObjectsFolder);
		
		//Use square root function
		Double value = 16.00;
		sqrt(value);

		// VariableNode node = client.getAddressSpace().createVariableNode(new NodeId(2,
		// "TestFolder"));
		// System.out.println(node.getBrowseName().get().getName()+":"+node.getNodeClass().get().toString());
		
		//Get History from Variable
		//getHistory(client);
		
		//Get CustomDataTypeVariable's value
		getNodeValue("TestFolder/CustomDataTypeVariable");
		
		// complete client's work
		//ClientRunner.closeOpcUaClient(opcUaClient);
	}
	
	private void getNodeValue(String identifier) throws Exception{
		registerCustomCodec();
		
		NodeId nodeId = new NodeId(2, identifier);
		
		ExtensionObject xo = (ExtensionObject) opcUaClient.readValue(
				0.0, 
				TimestampsToReturn.Both, 
				nodeId
			).get().getValue().getValue();
		
		CustomDataType value = xo.decode();
		
		System.out.println("The value of the CustomDataTypeVariable is: " + value);
		
		VariableNode node = opcUaClient.getAddressSpace().createVariableNode(nodeId);
		
		// change and write new value
		CustomDataType modified = new CustomDataType("US", uint(30), true);
		ExtensionObject modifiedXo = ExtensionObject.encode(modified, xo.getEncodingTypeId());
		StatusCode writeStatus = node.writeValue(new DataValue(new Variant(modifiedXo))).get();
		logger.info("writeStatus={}", writeStatus);
	}

	private void registerCustomCodec() {
		//Create dictionary, binaryEncodingId and register the codec under that id
		OpcUaBinaryDataTypeDictionary dictionary = new OpcUaBinaryDataTypeDictionary(
				"urn:ca:uwo:ktsiouni:test-module:custom-data-type");
				
		NodeId binaryEncodingId = new NodeId(2, "DataType.CustomDataType.BinaryEncoding");
				
		dictionary.registerStructCodec(
				new CustomDataType.Codec().asBinaryCodec(), 
				"CustomDataType", 
				binaryEncodingId
		);
				
		//Register dictionary with the shared DataTypeManager instance
		OpcUaDataTypeManager.getInstance().registerTypeDictionary(dictionary);
	}

	private void getHistory() throws Exception {
		HistoryReadDetails historyReadDetails = new ReadRawModifiedDetails(
				false, 
				DateTime.MIN_VALUE, 
				DateTime.now(), 
				uint(0), 
				true
		);
		
		HistoryReadValueId historyReadValueId = new HistoryReadValueId(
				new NodeId(2, "TestFolder/TestSubfolder1/TestVariable_1_0"), 
				null, 
				QualifiedName.NULL_VALUE, 
				ByteString.NULL_VALUE
		);
		
		List<HistoryReadValueId> nodesToRead = new ArrayList<HistoryReadValueId>();
		nodesToRead.add(historyReadValueId);
		
		HistoryReadResponse historyReadResponse = opcUaClient.historyRead(
				historyReadDetails, 
				TimestampsToReturn.Both, 
				false, 
				nodesToRead
		).get();
		
		HistoryReadResult[] historyReadResults = historyReadResponse.getResults();
		
		if (historyReadResults != null) {
			HistoryReadResult historyReadResult = historyReadResults[0];

			if (historyReadResult.getStatusCode().isGood()==true) {
				HistoryData historyData = historyReadResult.getHistoryData().decode();
				List<DataValue> dataValues = l(historyData.getDataValues());
				dataValues.forEach(v -> System.out.println("value="+v));
			} else {
				logger.info("Status Code:{}", historyReadResult.getStatusCode());
			}
		}
	}

	private void sqrt(Double value) throws Exception{
		NodeId objectId = NodeId.parse("ns=2;s=TestFolder");
		NodeId methodId = NodeId.parse("ns=2;s=TestFolder/sqrt(x)");
		
		CallMethodRequest request = new CallMethodRequest(objectId, methodId, new Variant[] {new Variant(value)});
		
		CallMethodResult result = opcUaClient.call(request).get();
		if (result.getStatusCode().isGood()) {
			Double resultValue = (Double) result.getOutputArguments()[0].getValue();
			System.out.println("Sqrt("+value+"): "+resultValue);
		} else {
			System.out.println("Something went wrong with the calculation!!!");
		}
	}

	private void readValues() throws Exception{
		List<NodeId> nodeIds = new ArrayList<NodeId>();
		
		for (int i = 0; i < 5; i++) {
			nodeIds.add(new NodeId(2, "TestFolder/TestSubfolder1/TestVariable_1_"+i));
		}
		
		List<DataValue> values = opcUaClient.readValues(0.0, TimestampsToReturn.Both, nodeIds).get();
		
		values.forEach(a -> {
			System.out.println("Got value: "+ a.getValue().getValue());
		});
	}

	private void writeValues() throws Exception {
		List<NodeId> nodeIds;

		for (int i = 0; i < 5; i++) {
			nodeIds = ImmutableList.of(new NodeId(2, "TestFolder/TestSubfolder1/TestVariable_1_"+i));
			Variant v = new Variant(i);

			// don't write status or timestamps
			DataValue dv = new DataValue(v, null, null);

			// write asynchronously....
			CompletableFuture<List<StatusCode>> f = opcUaClient.writeValues(nodeIds, ImmutableList.of(dv));

			// ...but block for the results so we write in order
			List<StatusCode> statusCodes = f.get();
			StatusCode status = statusCodes.get(0);

			if (status.isGood()) {
				logger.info("Wrote '{}' to nodeId={}", v, nodeIds.get(0));
			}
		}
	}
	
	private void addNodes() {
		List<AddNodesItem> nodesToAdd = new ArrayList<AddNodesItem>();
		
		//Add a folder node
		NodeId nodeId = new NodeId(3, "TestSubfolder2");
		AddNodesItem nodeToAdd = new AddNodesItem(
				new NodeId(2, "TestFolder").expanded(), 
				Identifiers.Organizes, 
				nodeId.expanded(), 
				new QualifiedName(3, "TestSubfolder2"), 
				NodeClass.ObjectType, 
				null, 
				Identifiers.BaseObjectType.expanded());
		nodesToAdd.add(nodeToAdd);
		
		//Add a variable to it
		NodeId nodeId2 = new NodeId(3, "TestVariable_2_0");
		AddNodesItem nodeToAdd2 = new AddNodesItem(
				new NodeId(3, "TestSubfolder2").expanded(), 
				Identifiers.Organizes, 
				nodeId2.expanded(), 
				new QualifiedName(3, "TestVariable_2_0"), 
				NodeClass.VariableType, 
				null, 
				Identifiers.BaseDataVariableType.expanded());
		nodesToAdd.add(nodeToAdd2);
		
		opcUaClient.addNodes(nodesToAdd);
	}

	private void browseNode(String indent, NodeId browseRoot) {
		try {
			List<Node> nodes = opcUaClient.getAddressSpace().browse(browseRoot).get();

			for (Node node : nodes) {
				//if (node.getNodeId().get().getNamespaceIndex().intValue() == 2) {
					logger.info("{} Node={}, ns={}", indent, node.getBrowseName().get().getName(), node.getNodeId().get().getNamespaceIndex());
					//logger.info("{} NodeAttr={}", indent, node.getDescription().get().getText());
					//System.out.println(node.getNodeId().get().getNamespaceIndex());

					// recursively browse to children
				//}
				browseNode(indent + "  ", node.getNodeId().get());
			}
		} catch (InterruptedException | ExecutionException e) {
			logger.error("Browsing nodeId={} failed: {}", browseRoot, e.getMessage(), e);
		}
	}

	@Override
	//Override to set proper endpoint
	public String getEndpointUrl() {
		return "opc.tcp://localhost:12686/test";
	}

}
