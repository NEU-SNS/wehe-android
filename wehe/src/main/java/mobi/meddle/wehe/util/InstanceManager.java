package mobi.meddle.wehe.util;

import java.util.HashMap;

public class InstanceManager {

    private static final HashMap<String, Instance> instance_list = new HashMap<String, Instance>() {{
        put("rajesh", new Instance("54.200.20.20", "rajesh", "~"));
    }};

    static public Instance getInstance(String key) {
        return instance_list.get(key);
    }
}
