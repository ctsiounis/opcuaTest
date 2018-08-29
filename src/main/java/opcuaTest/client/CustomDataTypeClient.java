package opcuaTest.client;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.nodes.VariableNode;
import org.eclipse.milo.opcua.stack.core.types.OpcUaBinaryDataTypeDictionary;
import org.eclipse.milo.opcua.stack.core.types.OpcUaDataTypeManager;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExtensionObject;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opcuaTest.types.MyDataType;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

public class CustomDataTypeClient implements Client {
	
	private final Logger logger = LoggerFactory.getLogger(getClass());
	private OpcUaClient opcUaClient;
	ClientRunner runner;

	public static void main(String[] args) throws Exception {
		CustomDataTypeClient customClient = new CustomDataTypeClient();
		customClient.createNewOpcUaClient(customClient, 0);
		customClient.run();
		
	}
	
	public void setOpcUaClient(OpcUaClient opcUaClient) {
		this.opcUaClient = opcUaClient;
	}

	public void run() throws Exception {
		// register the codec for my custom class
		registerCustomCodec();
		
		// Check if opcUaClient and future have been initialized
		if (opcUaClient == null) {
			createNewOpcUaClient(this, 0);
		}
				
		// synchronous connect
		opcUaClient.connect().get();
			
		// Create variable node to perform actions
		VariableNode node = opcUaClient.getAddressSpace().createVariableNode(new NodeId(3, "AnotherFolder/MyDataTypeVariable"));
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
        //ClientRunner.closeOpcUaClient(opcUaClient);
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
