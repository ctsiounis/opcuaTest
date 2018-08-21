package opcuaTest.client;

import java.util.concurrent.CompletableFuture;

import org.eclipse.milo.examples.client.ClientExample;
import org.eclipse.milo.examples.server.types.CustomDataType;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.nodes.VariableNode;
import org.eclipse.milo.opcua.stack.core.types.OpcUaBinaryDataTypeDictionary;
import org.eclipse.milo.opcua.stack.core.types.OpcUaDataTypeManager;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExtensionObject;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opcuaTest.types.MyDataType;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

public class CustomDataTypeClient implements ClientExample {

	

	public static void main(String[] args) {
		CustomDataTypeClient client = new CustomDataTypeClient();
		new ClientRunner(client, 2).run();
	}
	
	private final Logger logger = LoggerFactory.getLogger(getClass());

	public void run(OpcUaClient client, CompletableFuture<OpcUaClient> future) throws Exception {
		// register the codec for my custom class
		registerCustomCodec();
		
		// synchronous connect
		client.connect().get();
			
		// Create variable node to perform actions
		VariableNode node = client.getAddressSpace().createVariableNode(new NodeId(3, "AnotherFolder/MyDataTypeVariable"));
		logger.info("DataType={}", node.getDataType().get());

        // Read the current value
        DataValue value = node.readValue().get();
        logger.info("Value={}", value);

        Variant variant = value.getValue();
        ExtensionObject xo = (ExtensionObject) variant.getValue();

        MyDataType decoded = xo.decode();
        logger.info("Decoded={}", decoded);
		
     // Write a modified value
        MyDataType modified = new MyDataType("US", uint(30), true);
        ExtensionObject modifiedXo = ExtensionObject.encode(
            modified,
            xo.getEncodingTypeId()
        );

        StatusCode writeStatus = node.writeValue(new DataValue(new Variant(modifiedXo))).get();

        logger.info("writeStatus={}", writeStatus);
        
     // Read the modified value back
        value = node.readValue().get();
        logger.info("Value={}", value);

        variant = value.getValue();
        xo = (ExtensionObject) variant.getValue();

        decoded = xo.decode();
        logger.info("Decoded={}", decoded);
		
		// complete client's work
		future.complete(client);
	}
	
	private void registerCustomCodec() {
		//Create dictionary, binaryEncodingId and register the codec under that id
		OpcUaBinaryDataTypeDictionary dictionary = new OpcUaBinaryDataTypeDictionary(
				"urn:ca:uwo:ktsiouni:another-module:my-data-type");
				
		NodeId binaryEncodingId = new NodeId(3, "DataType.MyDataType.BinaryEncoding");
				
		dictionary.registerStructCodec(
				new MyDataType.Codec().asBinaryCodec(), 
				"MyDataType", 
				binaryEncodingId
		);
				
		//Register dictionary with the shared DataTypeManager instance
		OpcUaDataTypeManager.getInstance().registerTypeDictionary(dictionary);
	}
	
	@Override
	//Override to set proper endpoint
	public String getEndpointUrl() {
		return "opc.tcp://localhost:12686/test";
	}

}
