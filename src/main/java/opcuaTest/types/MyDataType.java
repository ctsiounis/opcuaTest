package opcuaTest.types;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import org.eclipse.milo.opcua.stack.core.UaSerializationException;
import org.eclipse.milo.opcua.stack.core.serialization.UaDecoder;
import org.eclipse.milo.opcua.stack.core.serialization.UaEncoder;
import org.eclipse.milo.opcua.stack.core.serialization.codecs.GenericDataTypeCodec;
import org.eclipse.milo.opcua.stack.core.serialization.codecs.SerializationContext;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

public class MyDataType {

	private final String location;
    private final UInteger speed;
    private final boolean running;

    public MyDataType() {
        this(null, uint(0), false);
    }

    public MyDataType(String location, UInteger speed, boolean running) {
        this.location = location;
        this.speed = speed;
        this.running = running;
    }

    public String getLocation() {
        return location;
    }

    public UInteger getSpeed() {
        return speed;
    }

    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MyDataType that = (MyDataType) o;
        return running == that.running &&
            Objects.equal(location, that.location) &&
            Objects.equal(speed, that.speed);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(location, speed, running);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("location", location)
            .add("speed", speed)
            .add("running", running)
            .toString();
    }

    public static class Codec extends GenericDataTypeCodec<MyDataType> {
        @Override
        public Class<MyDataType> getType() {
            return MyDataType.class;
        }

        @Override
        public MyDataType decode(
            SerializationContext context,
            UaDecoder decoder) throws UaSerializationException {

            String location = decoder.readString("Location");
            UInteger speed = decoder.readUInt32("Speed");
            boolean running = decoder.readBoolean("Running");

            return new MyDataType(location, speed, running);
        }

        @Override
        public void encode(
            SerializationContext context,
            MyDataType myDataType,
            UaEncoder encoder) throws UaSerializationException {

            encoder.writeString("Location", myDataType.location);
            encoder.writeUInt32("Speed", myDataType.speed);
            encoder.writeBoolean("Running", myDataType.running);
        }
    }
}
