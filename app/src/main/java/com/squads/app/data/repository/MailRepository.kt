package com.squads.app.data.repository

import com.squads.app.data.MailApi
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

        suspend fun refreshMail() {
            val mail = mailApi.getMail()
            mailDao.insertMail(mail.map { it.toEntity() })
        }
    }
