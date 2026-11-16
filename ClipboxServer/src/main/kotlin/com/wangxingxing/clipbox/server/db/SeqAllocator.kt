/**
 * author : 王星星
 * date : 2026-11-16 13:44
 * email : 1099420259@qq.com
 * description : server_seq 分配器；读取 meta.server_seq 后加一并写回，必须在写事务内调用。
 *               注意：禁止在事务外预分配，否则序号与数据写入无法保持原子一致。
 */
package com.wangxingxing.clipbox.server.db

object SeqAllocator {

    fun nextSeq(): Long {
        val current = MetaRepository.getLong(MetaRepository.SERVER_SEQ_KEY)
            ?: error("meta.${MetaRepository.SERVER_SEQ_KEY} 缺失，数据库可能未完成迁移")
        val next = current + 1
        MetaRepository.put(MetaRepository.SERVER_SEQ_KEY, next.toString())
        return next
    }
}
