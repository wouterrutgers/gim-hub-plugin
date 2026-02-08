package gimhub;

public interface APISerializable {
    Object serialize();

    default APISerializable diff(APISerializable newer) {
        return this;
    }
}
