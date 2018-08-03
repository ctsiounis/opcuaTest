package opcuaTest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.eclipse.milo.examples.client.ClientExample;
import org.eclipse.milo.examples.client.ClientExampleRunner;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.nodes.Node;
import org.eclipse.milo.opcua.sdk.client.api.nodes.VariableNode;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.AddNodesItem;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodResult;
import org.eclipse.milo.opcua.stack.core.types.structured.HistoryData;
import org.eclipse.milo.opcua.stack.core.types.structured.HistoryReadDetails;
import org.eclipse.milo.opcua.stack.core.types.structured.HistoryReadResponse;
import org.eclipse.milo.opcua.stack.core.types.structured.HistoryReadResult;
import org.eclipse.milo.opcua.stack.core.types.structured.HistoryReadValueId;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadRawModifiedDetails;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.eclipse.milo.opcua.stack.core.types.structured.ViewNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.eclipse.milo.opcua.stack.core.util.ConversionUtil.l;

public class ClientTest implements ClientExample {

	public static void main(String[] args) throws Exception {
		ClientTest client = new ClientTest();
		new ClientExampleRunner(client, false).run();

	}

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public void run(OpcUaClient client, CompletableFuture<OpcUaClient> future) throws Exception {
		// synchronous connect
		client.connect().get();

		//Write values in already existing folder
		writeValues(client);
		
		//Read values in already existing folder
		readValues(client);
		
		//Add nodes
		addNodes(client);
		
		// browse nodes
		browseNode("", client, new NodeId(2, "TestFolder"));
		
		//Use square root function
		Double value = 16.00;
		sqrt(client, value);

		// VariableNode node = client.getAddressSpace().createVariableNode(new NodeId(2,
		// "TestFolder"));
		// System.out.println(node.getBrowseName().get().getName()+":"+node.getNodeClass().get().toString());
		
		//Get History from Variable
		getHistory(client);
		
		// complete client's work
		future.complete(client);
	}
	
	private void getHistory(OpcUaClient client) throws Exception {
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
		
		HistoryReadResponse historyReadResponse = client.historyRead(
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

	private void sqrt(OpcUaClient client, Double value) throws Exception{
		NodeId objectId = NodeId.parse("ns=2;s=TestFolder");
		NodeId methodId = NodeId.parse("ns=2;s=TestFolder/sqrt(x)");
		
		CallMethodRequest request = new CallMethodRequest(objectId, methodId, new Variant[] {new Variant(value)});
		
		CallMethodResult result = client.call(request).get();
		if (result.getStatusCode().isGood()) {
			Double resultValue = (Double) result.getOutputArguments()[0].getValue();
			System.out.println("Sqrt("+value+"): "+resultValue);
		} else {
			System.out.println("Something went wrong with the calculation!!!");
		}
	}

	private void readValues(OpcUaClient client) throws Exception{
		List<NodeId> nodeIds = new ArrayList<NodeId>();
		
		for (int i = 0; i < 5; i++) {
			nodeIds.add(new NodeId(2, "TestFolder/TestSubfolder1/TestVariable_1_"+i));
		}
		
		List<DataValue> values = client.readValues(0.0, TimestampsToReturn.Both, nodeIds).get();
		
		values.forEach(a -> {
			System.out.println("Got value: "+ a.getValue().getValue());
		});
	}

	private void writeValues(OpcUaClient client) throws Exception {
		List<NodeId> nodeIds;

		for (int i = 0; i < 5; i++) {
			nodeIds = ImmutableList.of(new NodeId(2, "TestFolder/TestSubfolder1/TestVariable_1_"+i));
			Variant v = new Variant(i+5);

			// don't write status or timestamps
			DataValue dv = new DataValue(v, null, null);

			// write asynchronously....
			CompletableFuture<List<StatusCode>> f = client.writeValues(nodeIds, ImmutableList.of(dv));

			// ...but block for the results so we write in order
			List<StatusCode> statusCodes = f.get();
			StatusCode status = statusCodes.get(0);

			if (status.isGood()) {
				logger.info("Wrote '{}' to nodeId={}", v, nodeIds.get(0));
			}
		}
	}
	
	private void addNodes(OpcUaClient client) {
		List<AddNodesItem> nodesToAdd = new ArrayList<AddNodesItem>();
		
		//Add a folder node
		NodeId nodeId = new NodeId(2, "TestSubfolder2");
		AddNodesItem nodeToAdd = new AddNodesItem(
				new NodeId(2, "TestFolder").expanded(), 
				Identifiers.Organizes, 
				nodeId.expanded(), 
				new QualifiedName(2, "TestSubfolder2"), 
				NodeClass.ObjectType, 
				null, 
				Identifiers.BaseObjectType.expanded());
		nodesToAdd.add(nodeToAdd);
		
		//Add a variable to it
		NodeId nodeId2 = new NodeId(2, "TestVariable_2_0");
		AddNodesItem nodeToAdd2 = new AddNodesItem(
				new NodeId(2, "TestSubfolder2").expanded(), 
				Identifiers.Organizes, 
				nodeId2.expanded(), 
				new QualifiedName(2, "TestVariable_2_0"), 
				NodeClass.VariableType, 
				null, 
				Identifiers.BaseDataVariableType.expanded());
		nodesToAdd.add(nodeToAdd2);
		
		client.addNodes(nodesToAdd);
	}

	private void browseNode(String indent, OpcUaClient client, NodeId browseRoot) {
		try {
			List<Node> nodes = client.getAddressSpace().browse(browseRoot).get();

			for (Node node : nodes) {
				//if (node.getNodeId().get().getNamespaceIndex().intValue() == 2) {
					logger.info("{} Node={}", indent, node.getBrowseName().get().getName());
					//logger.info("{} NodeAttr={}", indent, node.getDescription().get().getText());
					// System.out.println(node.getNodeId().get().getNamespaceIndex());

					// recursively browse to children
				//}
				browseNode(indent + "  ", client, node.getNodeId().get());
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
