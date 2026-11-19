/**
 * author : 王星星
 * date : 2026-11-16 13:44
 * email : 1099420259@qq.com
 * description : Exposed 表映射，列名与 V001__init.sql 保持一致；仅用于查询与写入，不负责建表。
 *               注意：建表与索引由迁移脚本负责，禁止用 SchemaUtils.create 代替迁移。
 */
package com.wangxingxing.clipbox.server.db

import org.jetbrains.exposed.v1.core.Table

object DevicesTable : Table("devices") {
    val id = text("id")
    val name = text("name")
    val tokenHash = text("token_hash")
    val createdAt = long("created_at")
    val lastSeenAt = long("last_seen_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

object ClipsTable : Table("clips") {
    val id = text("id")
    val content = text("content")
    val sourceDeviceId = text("source_device_id").nullable()
    val pinned = integer("pinned")
    val createdAt = long("created_at")
    val deletedAt = long("deleted_at").nullable()
    val serverSeq = long("server_seq")

    override val primaryKey = PrimaryKey(id)
}

object DevicePairingCodesTable : Table("device_pairing_codes") {
    val issuerId = text("issuer_id")
    val codeHash = text("code_hash")
    val createdAt = long("created_at")
    val expiresAt = long("expires_at")
    val usedAt = long("used_at").nullable()

    override val primaryKey = PrimaryKey(issuerId)
}

object SharesTable : Table("shares") {
    val tokenHash = text("token_hash")
    val clipId = text("clip_id")
    val content = text("content")
    val createdAt = long("created_at")
    val expiresAt = long("expires_at")
    val oneTime = integer("one_time")
    val openedAt = long("opened_at").nullable()

    override val primaryKey = PrimaryKey(tokenHash)
}

object MetaTable : Table("meta") {
    val key = text("key")
    val value = text("value")

    override val primaryKey = PrimaryKey(key)
}
