package com.yue.dubbing;

import com.tencentcloudapi.asr.v20190614.AsrClient;
import com.tencentcloudapi.asr.v20190614.models.*;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Ordinary Cantonese only. Never retry task creation: an ambiguous response may already be billed. */
public class TencentAsrGateway {
    public record Cue(long startMs, long endMs, String text) {}
    public record Result(String status, List<Cue> cues) {}
    private final AsrClient client;
    public TencentAsrGateway(AsrClient client) { this.client = client; }

    public long submit(byte[] audio) throws TencentCloudSDKException {
        if (audio.length == 0 || audio.length > 5 * 1024 * 1024)
            throw new IllegalArgumentException("识别音频必须在 5 MB 以内");
        var request = new CreateRecTaskRequest();
        request.setEngineModelType("16k_yue");
        request.setChannelNum(1L);
        request.setResTextFormat(3L);
        request.setSourceType(1L);
        request.setDataLen((long) audio.length);
        request.setData(Base64.getEncoder().encodeToString(audio));
        var response = client.CreateRecTask(request);
        if (response == null || response.getData() == null || response.getData().getTaskId() == null)
            throw new IllegalStateException("云端提交结果不明，请勿立即重复提交");
        return response.getData().getTaskId();
    }

    public Result poll(long id) throws TencentCloudSDKException {
        var request = new DescribeTaskStatusRequest(); request.setTaskId(id);
        var response = client.DescribeTaskStatus(request);
        if (response == null || response.getData() == null || response.getData().getStatus() == null)
            throw new IllegalStateException("识别服务响应异常");
        var task = response.getData();
        return switch (task.getStatus().intValue()) {
            case 0, 1 -> new Result("recognizing", List.of());
            case 3 -> new Result("failed", List.of());
            case 2 -> new Result("succeeded", mapSentences(task.getResultDetail()));
            default -> throw new IllegalStateException("识别服务返回未知状态");
        };
    }

    private List<Cue> mapSentences(SentenceDetail[] sentences) {
        if (sentences == null) return List.of();
        var cues = new ArrayList<Cue>();
        for (var sentence : sentences) {
            if (sentence == null || sentence.getFinalSentence() == null || sentence.getStartMs() == null ||
                sentence.getEndMs() == null || sentence.getStartMs() < 0 || sentence.getEndMs() <= sentence.getStartMs() ||
                sentence.getEndMs() > 301000)
                throw new IllegalStateException("识别结果的台词时间无效");
            if (!sentence.getFinalSentence().isBlank()) cues.add(new Cue(sentence.getStartMs(), sentence.getEndMs(), sentence.getFinalSentence().trim()));
        }
        return List.copyOf(cues);
    }
}
