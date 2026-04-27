package cn.maple.extension.stereotype;

import cn.maple.extension.GXExtension;

@GXExtension(bizId = "stereotype")
public class SingleAnnotatedExtension implements StereotypeExtPoint {
    @Override
    public String name() {
        return "single";
    }
}
