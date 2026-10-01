package com.wyrm.omrajput.data

/**
 * A native crash, readable (OM, 2026-10-01). On Android 12+ the
 * `ApplicationExitInfo` of a native crash carries the tombstone as protobuf
 * (AOSP `system/core/debuggerd/proto/tombstone.proto`). The first native crash
 * report (6.3.2, Moto g85) said only "native, status 11" and sent the logs of
 * the *next* launch, so nothing showed where it died. This turns the tombstone
 * into text: the signal and fault address, the abort message and causes, the
 * crashing thread's backtrace (function + offset, library, build id), the
 * other threads' top frames, and the last log lines of the crashed process.
 *
 * Field numbers (tombstone.proto): Tombstone tid 6, signal_info 10,
 * abort_message 14, causes 15, threads 16 (map<uint32, Thread>), log_buffers
 * 18. Signal number 1, name 2, code 3, code_name 4, has_fault_address 8,
 * fault_address 9. Cause human_readable 1. Thread id 1, name 2,
 * current_backtrace 4. BacktraceFrame rel_pc 1, function_name 4,
 * function_offset 5, file_name 6, build_id 8. LogBuffer name 1, logs 2.
 * LogMessage timestamp 1, tid 3, priority 4, tag 5, message 6.
 */
internal object Tombstone {
    /** One protobuf field: a varint (wire 0), or the bounds of a length-delimited value (wire 2). */
    private class Field(val number: Int, val varint: Long, val start: Int, val end: Int, val delimited: Boolean)

    private fun fields(b: ByteArray, from: Int, to: Int): List<Field> {
        val out = ArrayList<Field>()
        var p = from
        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (p < to && shift < 64) {
                val x = b[p++].toInt() and 0xFF
                result = result or ((x and 0x7F).toLong() shl shift)
                if (x < 0x80) return result
                shift += 7
            }
            return result
        }
        while (p < to) {
            val key = varint()
            val number = (key ushr 3).toInt()
            when ((key and 7).toInt()) {
                0 -> out += Field(number, varint(), 0, 0, false)
                1 -> p += 8
                2 -> {
                    val length = varint().toInt()
                    if (length < 0 || p + length > to) return out
                    out += Field(number, 0, p, p + length, true)
                    p += length
                }
                5 -> p += 4
                else -> return out
            }
        }
        return out
    }

    private fun ByteArray.text(f: Field) = String(this, f.start, f.end - f.start, Charsets.UTF_8)

    private fun hex(value: Long) = "0x" + java.lang.Long.toHexString(value)

    /** Text for the crash report, at most [limit] characters; null when this is not a tombstone. */
    fun describe(bytes: ByteArray, limit: Int = 56_000, logLines: Int = 120): String? {
        val top = runCatching { fields(bytes, 0, bytes.size) }.getOrNull() ?: return null
        val tid = top.firstOrNull { it.number == 6 && !it.delimited }?.varint?.toInt()
        val threads = top.filter { it.number == 16 && it.delimited }
        if (threads.isEmpty() && top.none { it.number == 10 }) return null
        val out = StringBuilder()

        top.firstOrNull { it.number == 10 && it.delimited }?.let { s ->
            val sig = fields(bytes, s.start, s.end)
            val number = sig.firstOrNull { it.number == 1 }?.varint
            val name = sig.firstOrNull { it.number == 2 && it.delimited }?.let { bytes.text(it) }.orEmpty()
            val code = sig.firstOrNull { it.number == 3 }?.varint
            val codeName = sig.firstOrNull { it.number == 4 && it.delimited }?.let { bytes.text(it) }.orEmpty()
            val hasFault = sig.firstOrNull { it.number == 8 }?.varint == 1L
            val fault = sig.firstOrNull { it.number == 9 }?.varint
            out.append("signal ").append(number ?: "?").append(" (").append(name).append("), code ")
                .append(code ?: "?").append(" (").append(codeName).append(')')
            if (hasFault && fault != null) out.append(", fault addr ").append(hex(fault))
            out.append('\n')
        }
        top.filter { it.number == 14 && it.delimited }.forEach { out.append("abort: ").append(bytes.text(it)).append('\n') }
        top.filter { it.number == 15 && it.delimited }.forEach { c ->
            fields(bytes, c.start, c.end).firstOrNull { it.number == 1 && it.delimited }?.let {
                out.append("cause: ").append(bytes.text(it)).append('\n')
            }
        }

        // map<uint32, Thread>: each entry is {1: key, 2: Thread}.
        val parsed = threads.mapNotNull { entry ->
            val e = fields(bytes, entry.start, entry.end)
            val value = e.firstOrNull { it.number == 2 && it.delimited } ?: return@mapNotNull null
            val t = fields(bytes, value.start, value.end)
            val id = t.firstOrNull { it.number == 1 }?.varint?.toInt() ?: e.firstOrNull { it.number == 1 }?.varint?.toInt() ?: -1
            val name = t.firstOrNull { it.number == 2 && it.delimited }?.let { bytes.text(it) }.orEmpty()
            val frames = t.filter { it.number == 4 && it.delimited }.mapIndexed { i, f ->
                val fr = fields(bytes, f.start, f.end)
                val relPc = fr.firstOrNull { it.number == 1 }?.varint ?: 0L
                val fn = fr.firstOrNull { it.number == 4 && it.delimited }?.let { bytes.text(it) }.orEmpty()
                val off = fr.firstOrNull { it.number == 5 }?.varint ?: 0L
                val file = fr.firstOrNull { it.number == 6 && it.delimited }?.let { bytes.text(it) }.orEmpty()
                val build = fr.firstOrNull { it.number == 8 && it.delimited }?.let { bytes.text(it) }.orEmpty()
                buildString {
                    append("  #").append(i.toString().padStart(2, '0')).append(" pc ")
                        .append(java.lang.Long.toHexString(relPc).padStart(16, '0')).append("  ").append(file)
                    if (fn.isNotEmpty()) append(" (").append(fn).append('+').append(off).append(')')
                    if (build.isNotEmpty()) append(" (BuildId: ").append(build).append(')')
                }
            }
            Triple(id, name, frames)
        }
        val crashing = parsed.firstOrNull { it.first == tid }
        crashing?.let { (id, name, frames) ->
            out.append("\ncrashing thread ").append(id).append(" \"").append(name).append("\":\n")
            frames.forEach { out.append(it).append('\n') }
        }
        parsed.filter { it !== crashing }.forEach { (id, name, frames) ->
            if (out.length > limit / 2) return@forEach
            out.append("\nthread ").append(id).append(" \"").append(name).append("\":\n")
            frames.take(6).forEach { out.append(it).append('\n') }
        }

        // The crashed process's own last log lines.
        val lines = ArrayList<String>()
        top.filter { it.number == 18 && it.delimited }.forEach { buffer ->
            fields(bytes, buffer.start, buffer.end).filter { it.number == 2 && it.delimited }.forEach { m ->
                val f = fields(bytes, m.start, m.end)
                val time = f.firstOrNull { it.number == 1 && it.delimited }?.let { bytes.text(it) }.orEmpty()
                val t = f.firstOrNull { it.number == 3 }?.varint ?: 0L
                val priority = when (f.firstOrNull { it.number == 4 }?.varint?.toInt()) {
                    2 -> 'V'; 3 -> 'D'; 4 -> 'I'; 5 -> 'W'; 6 -> 'E'; 7 -> 'F'; else -> '?'
                }
                val tag = f.firstOrNull { it.number == 5 && it.delimited }?.let { bytes.text(it) }.orEmpty()
                val message = f.firstOrNull { it.number == 6 && it.delimited }?.let { bytes.text(it) }.orEmpty().trimEnd()
                lines += "$time $t $priority $tag: $message"
            }
        }
        if (lines.isNotEmpty()) {
            out.append("\nlast log lines of the crashed process:\n")
            lines.sorted().takeLast(logLines).forEach { out.append(it).append('\n') }
        }
        return out.toString().take(limit)
    }
}
