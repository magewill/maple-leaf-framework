package cn.maple.sso.captcha;

import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;

public interface ICaptcha extends Serializable {
    void generate(HttpServletRequest request, OutputStream out, String ticket) throws IOException;

    boolean verification(HttpServletRequest request, String ticket, String captcha);
}
