package com.yue.dubbing;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.concurrent.Semaphore;

/** Runs after Spring Security, before lazy multipart parsing. */
public class DubbingUploadFilter extends OncePerRequestFilter {
    private final DubbingAsrProperties properties;
    private final DubbingTranscriptionService service;
    private final Semaphore uploading = new Semaphore(1);
    public DubbingUploadFilter(DubbingAsrProperties properties, DubbingTranscriptionService service) {
        this.properties = properties; this.service = service;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        boolean dubbing = request.getMethod().equals("POST") && request.getRequestURI().equals(request.getContextPath() + "/api/dubbing/transcriptions");
        boolean multipart = request.getContentType() != null && request.getContentType().toLowerCase(java.util.Locale.ROOT).startsWith("multipart/");
        String path=request.getRequestURI().substring(request.getContextPath().length());
        boolean adminAudio=(request.getMethod().equals("POST")&&path.equals("/api/admin/audio")) || (request.getMethod().equals("PUT")&&path.matches("/api/admin/audio/[0-9]+"));
        if (!dubbing && !multipart) { chain.doFilter(request, response); return; }
        if (dubbing && !properties.isEnabled()) { reject(response, 503, "云端粤语识别尚未开通，请先导入 SRT 或手动添加台词"); return; }
        long length = request.getContentLengthLong();
        if (length < 0) { reject(response, 411, "上传需要提供文件长度"); return; }
        if (length > (dubbing ? 201L : adminAudio ? 21L : 2L) * 1024 * 1024) { reject(response, 413, "上传文件超过大小限制"); return; }
        if (!dubbing) { chain.doFilter(request, response); return; }
        if (!uploading.tryAcquire()) { reject(response, 503, "识别服务正忙，请稍后重试"); return; }
        try {
            if (!service.available()) { reject(response, 503, "识别服务正忙，请稍后重试"); return; }
            chain.doFilter(request, response);
        } finally { uploading.release(); }
    }
    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status); response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
