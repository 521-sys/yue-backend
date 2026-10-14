package com.yue.dubbing;

import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.YearMonth;
import java.io.IOException;

/** Conservative durable reservations, including failed/ambiguous submissions. Single-host deployment. */
public class AsrBudget {
    private final Path file;
    private final long limit;
    private final Clock clock;
    public AsrBudget(Path file, long limit, Clock clock) { this.file = file; this.limit = limit; this.clock = clock; }

    public synchronized void reserve(long seconds) throws IOException {
        if (seconds <= 0 || seconds > limit) throw new IllegalStateException("本月识别试用额度不足，请联系管理员");
        Files.createDirectories(file.toAbsolutePath().getParent());
        try (var lockChannel = FileChannel.open(file.resolveSibling(file.getFileName() + ".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = lockChannel.lock()) {
            String month = YearMonth.now(clock).toString();
            long used = 0;
            if (Files.exists(file)) {
                String[] fields = Files.readString(file).trim().split(" ");
                if (fields.length != 2) throw new IOException("识别预算记录损坏");
                YearMonth.parse(fields[0]);
                long stored = Long.parseLong(fields[1]);
                if (stored < 0) throw new IOException("识别预算记录损坏");
                if (fields[0].equals(month)) used = stored;
            }
            if (used > limit - seconds) throw new IllegalStateException("本月识别试用额度不足，请联系管理员");
            Path temp = Files.createTempFile(file.toAbsolutePath().getParent(), "budget-", ".tmp");
            try {
                try (var output = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                    output.write(ByteBuffer.wrap((month + " " + (used + seconds)).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                    output.force(true);
                }
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(temp); }
        }
    }
}
