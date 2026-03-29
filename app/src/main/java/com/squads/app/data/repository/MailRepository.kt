package com.squads.app.data.repository

import com.squads.app.data.MailApi
import com.squads.app.data.MailFolder
import com.squads.app.data.MailMessage
import com.squads.app.data.db.MailDao
import com.squads.app.data.db.toDomain
import com.squads.app.data.db.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MailRepository
    @Inject
    constructor(
        private val mailApi: MailApi,
        private val mailDao: MailDao,
    ) {
        fun observeMail(): Flow<List<MailMessage>> = mailDao.observeMail().map { entities -> entities.map { it.toDomain() } }

        fun observeMailByFolder(folderId: String): Flow<List<MailMessage>> =
            mailDao.observeMailByFolder(folderId).map { entities -> entities.map { it.toDomain() } }

        suspend fun refreshMail(folderId: String? = null) {
            val mail = mailApi.getMail(folderId = folderId)
            if (folderId != null) {
                mailDao.replaceMailInFolder(folderId, mail.map { it.toEntity() })
            } else {
                mailDao.insertMail(mail.map { it.toEntity() })
            }
        }

        suspend fun getMailFolders(): List<MailFolder> = mailApi.getMailFolders()

        suspend fun getInboxFolderId(): String? = mailApi.getInboxFolderId()
    }
