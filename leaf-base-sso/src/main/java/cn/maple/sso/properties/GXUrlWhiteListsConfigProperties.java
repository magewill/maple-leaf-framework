package cn.maple.sso.properties;

import java.util.Collections;
import java.util.List;

public class GXUrlWhiteListsConfigProperties {
    protected List<String> whiteLists = Collections.emptyList();

    public List<String> getWhiteLists() {
        return whiteLists;
    }

    public void setWhiteLists(List<String> whiteLists) {
        this.whiteLists = whiteLists == null ? Collections.emptyList() : whiteLists;
    }
}
