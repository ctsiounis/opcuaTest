package opcuaTest.namespaces;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ubyte;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

import org.eclipse.milo.examples.server.types.CustomDataType;
import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.AccessContext;
import org.eclipse.milo.opcua.sdk.server.api.DataItem;
import org.eclipse.milo.opcua.sdk.server.api.MethodInvocationHandler;
import org.eclipse.milo.opcua.sdk.server.api.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.api.Namespace;
import org.eclipse.milo.opcua.sdk.server.api.AttributeManager.ReadContext;
import org.eclipse.milo.opcua.sdk.server.api.AttributeManager.WriteContext;
import org.eclipse.milo.opcua.sdk.server.nodes.AttributeContext;
import org.eclipse.milo.opcua.sdk.server.nodes.NodeFactory;
import org.eclipse.milo.opcua.sdk.server.nodes.ServerNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaDataTypeNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaMethodNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.OpcUaBinaryDataTypeDictionary;
import org.eclipse.milo.opcua.stack.core.types.OpcUaDataTypeManager;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExtensionObject;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.AddNodesItem;
import org.eclipse.milo.opcua.stack.core.types.structured.AddNodesResult;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.eclipse.milo.opcua.stack.core.types.structured.WriteValue;
import org.eclipse.milo.opcua.stack.core.util.FutureUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

import opcuaTest.types.MyDataType;

public class AnotherNamespace implements Namespace{
	public static final String NAMESPACE_URI = "urn:ca:uwo:another-module";

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final Random random = new Random();

	private final SubscriptionModel subscriptionModel;

	private final NodeFactory nodeFactory;

	private final OpcUaServer server;
	private final UShort namespaceIndex;

	public AnotherNamespace(OpcUaServer server, UShort namespaceIndex) {
		this.server = server;
		this.namespaceIndex = namespaceIndex;

		subscriptionModel = new SubscriptionModel(server, this);

		nodeFactory = new NodeFactory(server.getNodeMap(), server.getObjectTypeManager(),
				server.getVariableTypeManager());

		try {
			// Create a "AnotherFolder" folder and add it to the node manager
			NodeId folderNodeId = new NodeId(namespaceIndex, "AnotherFolder");

			UaFolderNode folderNode = new UaFolderNode(server.getNodeMap(), folderNodeId,
					new QualifiedName(namespaceIndex, "AnotherFolder"), LocalizedText.english("AnotherFolder"));

			server.getNodeMap().addNode(folderNode);

			// Make sure our new folder shows up under the server's Objects folder
			server.getUaNamespace().addReference(Identifiers.ObjectsFolder, Identifiers.Organizes, true,
					folderNodeId.expanded(), NodeClass.Object);


			addCustomDataTypeVariable(folderNode);
			
			//addCustomObjectTypeAndInstance(folderNode);
		} catch (UaException e) {
			logger.error("Error adding nodes: {}", e.getMessage(), e);
		}
	}
	
	private void addCustomDataTypeVariable(UaFolderNode rootFolder) {
		//Custom DataTypeNode - subtype of DataTypeNode
		NodeId dataTypeId = new NodeId(namespaceIndex, "DataType.MyDataType");
		
		UaDataTypeNode dataTypeNode = new UaDataTypeNode(
				server.getNodeMap(), 
				dataTypeId, 
				new QualifiedName(namespaceIndex, "MyDataType"), 
				LocalizedText.english("MyDataType"), 
				LocalizedText.english("MyDataType"), 
				uint(0), 
				uint(0), 
				false
		);
		
		//Inverse ref to Structure
		dataTypeNode.addReference(new Reference(
				dataTypeId, 
				Identifiers.HasSubtype, 
				Identifiers.Structure.expanded(), 
				NodeClass.DataType, 
				false
		));
		
		//Forward ref from Structure
		Optional<UaDataTypeNode> structureDataTypeNode = server.getNodeMap()
				.getNode(Identifiers.Structure)
				.map(UaDataTypeNode.class::cast);
		
		structureDataTypeNode.ifPresent(node ->
				node.addReference(new Reference(
						node.getNodeId(), 
						Identifiers.HasSubtype, 
						dataTypeId.expanded(), 
						NodeClass.DataType, 
						true
				))
		);
		
		//Create dictionary, binaryEncodingId and register the codec under that id
		OpcUaBinaryDataTypeDictionary dictionary = new OpcUaBinaryDataTypeDictionary(
				"urn:ca:uwo:ktsiouni:another-module:my-data-type");
		
		NodeId binaryEncodingId = new NodeId(namespaceIndex, "DataType.MyDataType.BinaryEncoding");
		
		dictionary.registerStructCodec(
				new MyDataType.Codec().asBinaryCodec(), 
				"MyDataType", 
				binaryEncodingId
		);
		
		//Register dictionary with the shared DataTypeManager instance
		OpcUaDataTypeManager.getInstance().registerTypeDictionary(dictionary);
		
		UaVariableNode myDataTypeVariable = UaVariableNode.builder(server.getNodeMap())
				.setNodeId(new NodeId(namespaceIndex, "AnotherFolder/MyDataTypeVariable"))
				.setAccessLevel(ubyte(AccessLevel.getMask(AccessLevel.READ_WRITE)))
				.setUserAccessLevel(ubyte(AccessLevel.getMask(AccessLevel.READ_WRITE)))
				.setBrowseName(new QualifiedName(namespaceIndex, "MyDataTypeVariable"))
				.setDisplayName(LocalizedText.english("MyDataTypeVariable"))
				.setDataType(dataTypeId)
				.setTypeDefinition(Identifiers.BaseDataVariableType)
				.build();
		
		//Can create whatever type we want
		MyDataType value = new MyDataType("CA", uint(25), true);
		
		ExtensionObject xo = ExtensionObject.encode(value, binaryEncodingId);
		
		myDataTypeVariable.setValue(new DataValue(new Variant(xo)));
		
		rootFolder.addOrganizes(myDataTypeVariable);
		
		myDataTypeVariable.addReference(new Reference(
				myDataTypeVariable.getNodeId(), 
				Identifiers.Organizes, 
				rootFolder.getNodeId().expanded(), 
				rootFolder.getNodeClass(), 
				false
		));
	}

	@Override
	public UShort getNamespaceIndex() {
		return namespaceIndex;
	}

	@Override
	public String getNamespaceUri() {
		return NAMESPACE_URI;
	}

	@Override
	public CompletableFuture<List<Reference>> browse(AccessContext context, NodeId nodeId) {
		ServerNode node = server.getNodeMap().get(nodeId);

		if (node != null) {
			return CompletableFuture.completedFuture(node.getReferences());
		} else {
			return FutureUtils.failedFuture(new UaException(StatusCodes.Bad_NodeIdUnknown));
		}
	}

	@Override
	public void read(ReadContext context, Double maxAge, TimestampsToReturn timestamps,
			List<ReadValueId> readValueIds) {

		List<DataValue> results = Lists.newArrayListWithCapacity(readValueIds.size());

		for (ReadValueId readValueId : readValueIds) {
			ServerNode node = server.getNodeMap().get(readValueId.getNodeId());

			if (node != null) {
				DataValue value = node.readAttribute(new AttributeContext(context), readValueId.getAttributeId(),
						timestamps, readValueId.getIndexRange(), readValueId.getDataEncoding());

				results.add(value);
			} else {
				results.add(new DataValue(StatusCodes.Bad_NodeIdUnknown));
			}
		}

		context.complete(results);
	}

	@Override
	public void write(WriteContext context, List<WriteValue> writeValues) {
		List<StatusCode> results = Lists.newArrayListWithCapacity(writeValues.size());

		for (WriteValue writeValue : writeValues) {
			ServerNode node = server.getNodeMap().get(writeValue.getNodeId());

			if (node != null) {
				try {
					node.writeAttribute(new AttributeContext(context), writeValue.getAttributeId(),
							writeValue.getValue(), writeValue.getIndexRange());

					results.add(StatusCode.GOOD);

					logger.info("Wrote value {} to {} attribute of {}", writeValue.getValue().getValue(),
							AttributeId.from(writeValue.getAttributeId()).map(Object::toString).orElse("unknown"),
							node.getNodeId());
				} catch (UaException e) {
					logger.error("Unable to write value={}", writeValue.getValue(), e);
					results.add(e.getStatusCode());
				}
			} else {
				results.add(new StatusCode(StatusCodes.Bad_NodeIdUnknown));
			}
		}

		context.complete(results);
	}

	@Override
	public void onDataItemsCreated(List<DataItem> dataItems) {
		subscriptionModel.onDataItemsCreated(dataItems);
	}

	@Override
	public void onDataItemsModified(List<DataItem> dataItems) {
		subscriptionModel.onDataItemsModified(dataItems);
	}

	@Override
	public void onDataItemsDeleted(List<DataItem> dataItems) {
		subscriptionModel.onDataItemsDeleted(dataItems);
	}

	@Override
	public void onMonitoringModeChanged(List<MonitoredItem> monitoredItems) {
		subscriptionModel.onMonitoringModeChanged(monitoredItems);
	}

	@Override
	public Optional<MethodInvocationHandler> getInvocationHandler(NodeId methodId) {
		Optional<ServerNode> node = server.getNodeMap().getNode(methodId);

		return node.flatMap(n -> {
			if (n instanceof UaMethodNode) {
				return ((UaMethodNode) n).getInvocationHandler();
			} else {
				return Optional.empty();
			}
		});
	}

	@Override
	public void addNode(AddNodesContext context, List<AddNodesItem> nodesToAdd) {
		List<AddNodesResult> results = new ArrayList<AddNodesResult>();

		for (AddNodesItem nodeToAdd : nodesToAdd) {
			NodeId nodeId = new NodeId(
					nodeToAdd.getRequestedNewNodeId().getNamespaceIndex(),
					(String) nodeToAdd.getRequestedNewNodeId().getIdentifier()
			);
			System.out.println("Trying to add node:"+ nodeId.getIdentifier());
			try {
				
				UaFolderNode node = new UaFolderNode(
						server.getNodeMap(), 
						nodeId, 
						nodeToAdd.getBrowseName(),
						LocalizedText.english(nodeToAdd.getBrowseName().getName())
				);
				server.getNodeMap().addNode(node);

				NodeId parentNodeId = new NodeId(
						nodeToAdd.getParentNodeId().getNamespaceIndex(),
						(String) nodeToAdd.getParentNodeId().getIdentifier()
				);

				server.getUaNamespace().addReference(
						parentNodeId, 
						Identifiers.Organizes, 
						true, 
						nodeId.expanded(),
						NodeClass.Object
				);

				results.add(new AddNodesResult(new StatusCode(StatusCodes.Good_EntryInserted), nodeId));
			} catch (UaException e) {
				logger.error("Error adding nodes: {}", e.getMessage(), e);
				results.add(new AddNodesResult(new StatusCode(StatusCodes.Bad_UnexpectedError), nodeId));
			}
		}
		context.complete(results);
	}
	
	

	
}
