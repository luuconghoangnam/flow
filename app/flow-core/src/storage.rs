use std::path::Path;

use rusqlite::{params, Connection, Result};
use serde::{Deserialize, Serialize};

use crate::model::DownloadTask;

#[derive(Debug, Clone)]
pub struct ChunkProgress {
    pub download_id: String,
    pub chunk_index: i64,
    pub start: u64,
    pub end_inclusive: u64,
    pub downloaded: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct QueueJobRecord {
    pub id: String,
    pub queue_id: i64,
    pub url: String,
    pub output_dir: String,
    pub file_name: String,
    pub category: String,
    pub connections: usize,
    pub expected_sha256_hex: Option<String>,
    pub headers_json: Option<String>,
    pub referrer: Option<String>,
    pub cookies: Option<String>,
    pub user_agent: Option<String>,
    pub username: Option<String>,
    pub password: Option<String>,
    pub proxy_url: Option<String>,
    pub proxy_username: Option<String>,
    pub proxy_password: Option<String>,
    pub status: String,
    pub priority: i64,
    pub queue_order: i64,
    pub attempt_count: i64,
    pub last_error: Option<String>,
}

#[derive(Debug, Clone)]
pub struct QueueViewRow {
    pub id: String,
    pub queue_id: i64,
    pub queue_name: String,
    pub file_name: String,
    pub status: String,
    pub attempt_count: i64,
    pub downloaded_bytes: u64,
    pub total_bytes: Option<u64>,
    pub last_error: Option<String>,
    pub created_at: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct QueueGroupRecord {
    pub id: i64,
    pub name: String,
    pub max_concurrent: i64,
    pub stop_on_empty: bool,
    pub active: bool,
    pub schedule_json: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct QueueRuntimeEvent {
    pub queue_id: i64,
    pub event_type: String,
    pub payload_json: Option<String>,
    pub created_at: i64,
}

pub trait DownloadRepository {
    fn init_schema(&self) -> Result<()>;
    fn upsert_task(&self, task: &DownloadTask) -> Result<()>;
    fn upsert_chunk_progress(&self, chunk: &ChunkProgress) -> Result<()>;
    fn list_chunk_progress(&self, download_id: &str) -> Result<Vec<ChunkProgress>>;
    fn delete_chunk_progress(&self, download_id: &str) -> Result<()>;
    fn upsert_queue_job(&self, job: &QueueJobRecord) -> Result<()>;
    fn ensure_default_queue_group(&self) -> Result<()>;
    fn list_queue_groups(&self) -> Result<Vec<QueueGroupRecord>>;
    fn get_queue_group(&self, queue_id: i64) -> Result<Option<QueueGroupRecord>>;
    fn set_queue_group_active(&self, queue_id: i64, active: bool) -> Result<()>;
    fn upsert_queue_group(&self, group: &QueueGroupRecord) -> Result<()>;
    fn delete_queue_group(&self, queue_id: i64) -> Result<()>;
    fn log_queue_event(&self, queue_id: i64, event_type: &str, payload_json: Option<&str>) -> Result<()>;
    fn list_recent_queue_events(&self, limit: usize) -> Result<Vec<QueueRuntimeEvent>>;
    fn update_queue_job_status(&self, id: &str, status: &str) -> Result<()>;
    fn update_queue_job_category(&self, id: &str, category: &str) -> Result<()>;
    fn update_queue_job_attempt(&self, id: &str, attempt_count: i64, last_error: Option<&str>) -> Result<()>;
    fn get_queue_job(&self, id: &str) -> Result<Option<QueueJobRecord>>;
    fn list_queue_jobs(&self) -> Result<Vec<QueueJobRecord>>;
    fn list_queue_view_rows(&self) -> Result<Vec<QueueViewRow>>;
    fn reset_queue_job_for_retry(&self, id: &str) -> Result<()>;
    fn cleanup_queue_jobs_by_status(&self, statuses: &[String]) -> Result<usize>;
    fn delete_download_job(&self, id: &str) -> Result<()>;
    fn reorder_queue_job(&self, id: &str, direction: i64) -> Result<()>;
    fn push_queue_job_to_end(&self, id: &str) -> Result<()>;
    fn move_queue_job_to_index(&self, id: &str, target_index: usize) -> Result<()>;
    fn list_recoverable_jobs(&self) -> Result<Vec<QueueJobRecord>>;
}

pub struct SqliteDownloadRepository {
    connection: Connection,
}

impl SqliteDownloadRepository {
    pub fn open(path: &Path) -> Result<Self> {
        let connection = Connection::open(path)?;
        connection.pragma_update(None, "journal_mode", "WAL")?;
        Ok(Self { connection })
    }
}

impl DownloadRepository for SqliteDownloadRepository {
    fn init_schema(&self) -> Result<()> {
        self.connection.execute_batch(
            "
            CREATE TABLE IF NOT EXISTS downloads (
                id TEXT PRIMARY KEY,
                url TEXT NOT NULL,
                output_path TEXT NOT NULL,
                file_name TEXT NOT NULL,
                total_bytes INTEGER,
                downloaded_bytes INTEGER NOT NULL,
                status TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS download_chunks (
                download_id TEXT NOT NULL,
                chunk_index INTEGER NOT NULL,
                start_byte INTEGER NOT NULL,
                end_inclusive INTEGER NOT NULL,
                downloaded INTEGER NOT NULL,
                PRIMARY KEY(download_id, chunk_index)
            );

            CREATE TABLE IF NOT EXISTS queue_jobs (
                id TEXT PRIMARY KEY,
                queue_id INTEGER NOT NULL DEFAULT 0,
                url TEXT NOT NULL,
                output_dir TEXT NOT NULL,
                file_name TEXT NOT NULL,
                category TEXT NOT NULL DEFAULT 'General',
                connections INTEGER NOT NULL,
                expected_sha256_hex TEXT,
                headers_json TEXT,
                referrer TEXT,
                cookies TEXT,
                user_agent TEXT,
                username TEXT,
                password TEXT,
                proxy_url TEXT,
                proxy_username TEXT,
                proxy_password TEXT,
                status TEXT NOT NULL,
                priority INTEGER NOT NULL DEFAULT 0,
                queue_order INTEGER NOT NULL DEFAULT 0,
                attempt_count INTEGER NOT NULL DEFAULT 0,
                last_error TEXT,
                created_at INTEGER NOT NULL DEFAULT (unixepoch()),
                updated_at INTEGER NOT NULL DEFAULT (unixepoch())
            );

            CREATE TABLE IF NOT EXISTS queue_groups (
                id INTEGER PRIMARY KEY,
                name TEXT NOT NULL,
                max_concurrent INTEGER NOT NULL DEFAULT 3,
                stop_on_empty INTEGER NOT NULL DEFAULT 0,
                active INTEGER NOT NULL DEFAULT 1
            );

            CREATE TABLE IF NOT EXISTS queue_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                queue_id INTEGER NOT NULL,
                event_type TEXT NOT NULL,
                payload_json TEXT,
                created_at INTEGER NOT NULL DEFAULT (unixepoch())
            );

            ",
        )?;
        add_optional_column(&self.connection, "queue_jobs", "attempt_count", "INTEGER NOT NULL DEFAULT 0")?;
        add_optional_column(&self.connection, "queue_jobs", "queue_id", "INTEGER NOT NULL DEFAULT 0")?;
        add_optional_column(&self.connection, "queue_jobs", "category", "TEXT NOT NULL DEFAULT 'General'")?;
        add_optional_column(&self.connection, "queue_jobs", "last_error", "TEXT")?;
        add_optional_column(&self.connection, "queue_jobs", "headers_json", "TEXT")?;
        add_optional_column(&self.connection, "queue_jobs", "referrer", "TEXT")?;
        add_optional_column(&self.connection, "queue_jobs", "cookies", "TEXT")?;
        add_optional_column(&self.connection, "queue_jobs", "user_agent", "TEXT")?;
        add_optional_column(&self.connection, "queue_jobs", "username", "TEXT")?;
        add_optional_column(&self.connection, "queue_jobs", "password", "TEXT")?;
        add_optional_column(&self.connection, "queue_jobs", "proxy_url", "TEXT")?;
        add_optional_column(&self.connection, "queue_jobs", "proxy_username", "TEXT")?;
        add_optional_column(&self.connection, "queue_jobs", "proxy_password", "TEXT")?;
        add_optional_column(&self.connection, "queue_jobs", "queue_order", "INTEGER NOT NULL DEFAULT 0")?;
        self.ensure_default_queue_group()?;
        add_optional_column(&self.connection, "queue_groups", "max_concurrent", "INTEGER NOT NULL DEFAULT 3")?;
        add_optional_column(&self.connection, "queue_groups", "stop_on_empty", "INTEGER NOT NULL DEFAULT 0")?;
        add_optional_column(&self.connection, "queue_groups", "active", "INTEGER NOT NULL DEFAULT 1")?;
        add_optional_column(&self.connection, "queue_groups", "schedule_json", "TEXT")?;
        Ok(())
    }

    fn upsert_task(&self, task: &DownloadTask) -> Result<()> {
        self.connection.execute(
            "
            INSERT INTO downloads (id, url, output_path, file_name, total_bytes, downloaded_bytes, status)
            VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)
            ON CONFLICT(id) DO UPDATE SET
                total_bytes = excluded.total_bytes,
                downloaded_bytes = excluded.downloaded_bytes,
                status = excluded.status
            ",
            (
                &task.id.0,
                &task.url,
                task.output_path.to_string_lossy().to_string(),
                &task.file_name,
                task.total_bytes,
                task.downloaded_bytes,
                format!("{:?}", task.status),
            ),
        )?;
        Ok(())
    }

    fn upsert_chunk_progress(&self, chunk: &ChunkProgress) -> Result<()> {
        self.connection.execute(
            "
            INSERT INTO download_chunks (download_id, chunk_index, start_byte, end_inclusive, downloaded)
            VALUES (?1, ?2, ?3, ?4, ?5)
            ON CONFLICT(download_id, chunk_index) DO UPDATE SET
                end_inclusive = excluded.end_inclusive,
                downloaded = excluded.downloaded
            ",
            (
                &chunk.download_id,
                chunk.chunk_index,
                chunk.start,
                chunk.end_inclusive,
                chunk.downloaded,
            ),
        )?;
        Ok(())
    }

    fn list_chunk_progress(&self, download_id: &str) -> Result<Vec<ChunkProgress>> {
        let mut statement = self.connection.prepare(
            "
            SELECT download_id, chunk_index, start_byte, end_inclusive, downloaded
            FROM download_chunks
            WHERE download_id = ?1
            ORDER BY chunk_index ASC
            ",
        )?;
        let rows = statement.query_map([download_id], |row| {
            Ok(ChunkProgress {
                download_id: row.get(0)?,
                chunk_index: row.get(1)?,
                start: row.get(2)?,
                end_inclusive: row.get(3)?,
                downloaded: row.get(4)?,
            })
        })?;

        let mut chunks = Vec::new();
        for row in rows {
            chunks.push(row?);
        }
        Ok(chunks)
    }

    fn delete_chunk_progress(&self, download_id: &str) -> Result<()> {
        self.connection.execute(
            "DELETE FROM download_chunks WHERE download_id = ?1",
            params![download_id],
        )?;
        Ok(())
    }

    fn upsert_queue_job(&self, job: &QueueJobRecord) -> Result<()> {
        self.connection.execute(
            "
            INSERT INTO queue_jobs (id, queue_id, url, output_dir, file_name, category, connections, expected_sha256_hex, headers_json, referrer, cookies, user_agent, username, password, proxy_url, proxy_username, proxy_password, status, priority, queue_order, attempt_count, last_error)
            VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15, ?16, ?17, ?18, ?19, ?20, ?21, ?22)
            ON CONFLICT(id) DO UPDATE SET
                status = excluded.status,
                priority = excluded.priority,
                attempt_count = excluded.attempt_count,
                last_error = excluded.last_error,
                category = excluded.category,
                headers_json = excluded.headers_json,
                referrer = excluded.referrer,
                cookies = excluded.cookies,
                user_agent = excluded.user_agent,
                username = excluded.username,
                password = excluded.password,
                proxy_url = excluded.proxy_url,
                proxy_username = excluded.proxy_username,
                proxy_password = excluded.proxy_password,
                queue_order = excluded.queue_order,
                updated_at = unixepoch()
            ",
            params![
                &job.id,
                job.queue_id,
                &job.url,
                &job.output_dir,
                &job.file_name,
                &job.category,
                job.connections as i64,
                &job.expected_sha256_hex,
                &job.headers_json,
                &job.referrer,
                &job.cookies,
                &job.user_agent,
                &job.username,
                &job.password,
                &job.proxy_url,
                &job.proxy_username,
                &job.proxy_password,
                &job.status,
                job.priority,
                job.queue_order,
                job.attempt_count,
                &job.last_error,
            ],
        )?;
        Ok(())
    }

    fn ensure_default_queue_group(&self) -> Result<()> {
        self.connection.execute(
            "INSERT OR IGNORE INTO queue_groups (id, name, max_concurrent, stop_on_empty, active) VALUES (0, 'Main', 3, 0, 1)",
            [],
        )?;
        Ok(())
    }

    fn list_queue_groups(&self) -> Result<Vec<QueueGroupRecord>> {
        let mut statement = self.connection.prepare("SELECT id, name, max_concurrent, stop_on_empty, active, schedule_json FROM queue_groups ORDER BY id ASC")?;
        let rows = statement.query_map([], |row| {
            Ok(QueueGroupRecord {
                id: row.get(0)?,
                name: row.get(1)?,
                max_concurrent: row.get(2)?,
                stop_on_empty: row.get::<_, i64>(3)? != 0,
                active: row.get::<_, i64>(4)? != 0,
                schedule_json: row.get(5)?,
            })
        })?;
        let mut groups = Vec::new();
        for row in rows {
            groups.push(row?);
        }
        Ok(groups)
    }

    fn get_queue_group(&self, queue_id: i64) -> Result<Option<QueueGroupRecord>> {
        let mut statement = self.connection.prepare("SELECT id, name, max_concurrent, stop_on_empty, active, schedule_json FROM queue_groups WHERE id = ?1")?;
        let mut rows = statement.query([queue_id])?;
        if let Some(row) = rows.next()? {
            Ok(Some(QueueGroupRecord {
                id: row.get(0)?,
                name: row.get(1)?,
                max_concurrent: row.get(2)?,
                stop_on_empty: row.get::<_, i64>(3)? != 0,
                active: row.get::<_, i64>(4)? != 0,
                schedule_json: row.get(5)?,
            }))
        } else {
            Ok(None)
        }
    }

    fn set_queue_group_active(&self, queue_id: i64, active: bool) -> Result<()> {
        self.connection.execute(
            "UPDATE queue_groups SET active = ?2 WHERE id = ?1",
            (queue_id, i64::from(active)),
        )?;
        Ok(())
    }

    fn upsert_queue_group(&self, group: &QueueGroupRecord) -> Result<()> {
        self.connection.execute(
            "
            INSERT INTO queue_groups (id, name, max_concurrent, stop_on_empty, active, schedule_json)
            VALUES (?1, ?2, ?3, ?4, ?5, ?6)
            ON CONFLICT(id) DO UPDATE SET
                name = excluded.name,
                max_concurrent = excluded.max_concurrent,
                stop_on_empty = excluded.stop_on_empty,
                active = excluded.active,
                schedule_json = excluded.schedule_json
            ",
            params![group.id, &group.name, group.max_concurrent, i64::from(group.stop_on_empty), i64::from(group.active), &group.schedule_json],
        )?;
        Ok(())
    }

    fn delete_queue_group(&self, queue_id: i64) -> Result<()> {
        self.connection.execute("UPDATE queue_jobs SET queue_id = 0 WHERE queue_id = ?1", [queue_id])?;
        self.connection.execute("DELETE FROM queue_groups WHERE id = ?1 AND id != 0", [queue_id])?;
        Ok(())
    }

    fn log_queue_event(&self, queue_id: i64, event_type: &str, payload_json: Option<&str>) -> Result<()> {
        self.connection.execute(
            "INSERT INTO queue_events (queue_id, event_type, payload_json) VALUES (?1, ?2, ?3)",
            (queue_id, event_type, payload_json),
        )?;
        Ok(())
    }

    fn list_recent_queue_events(&self, limit: usize) -> Result<Vec<QueueRuntimeEvent>> {
        let mut statement = self.connection.prepare(
            "
            SELECT queue_id, event_type, payload_json, created_at
            FROM queue_events
            ORDER BY id DESC
            LIMIT ?1
            ",
        )?;
        let rows = statement.query_map([limit as i64], |row| {
            Ok(QueueRuntimeEvent {
                queue_id: row.get(0)?,
                event_type: row.get(1)?,
                payload_json: row.get(2)?,
                created_at: row.get(3)?,
            })
        })?;
        let mut events = Vec::new();
        for row in rows {
            events.push(row?);
        }
        Ok(events)
    }

    fn update_queue_job_status(&self, id: &str, status: &str) -> Result<()> {
        self.connection.execute(
            "UPDATE queue_jobs SET status = ?2, updated_at = unixepoch() WHERE id = ?1",
            (id, status),
        )?;
        Ok(())
    }

    fn update_queue_job_category(&self, id: &str, category: &str) -> Result<()> {
        self.connection.execute(
            "UPDATE queue_jobs SET category = ?2, updated_at = unixepoch() WHERE id = ?1",
            (id, category),
        )?;
        Ok(())
    }

    fn update_queue_job_attempt(&self, id: &str, attempt_count: i64, last_error: Option<&str>) -> Result<()> {
        self.connection.execute(
            "UPDATE queue_jobs SET attempt_count = ?2, last_error = ?3, updated_at = unixepoch() WHERE id = ?1",
            (id, attempt_count, last_error),
        )?;
        Ok(())
    }

    fn get_queue_job(&self, id: &str) -> Result<Option<QueueJobRecord>> {
        let mut statement = self.connection.prepare(
            "SELECT id, queue_id, url, output_dir, file_name, category, connections, expected_sha256_hex, headers_json, referrer, cookies, user_agent, username, password, proxy_url, proxy_username, proxy_password, status, priority, queue_order, attempt_count, last_error FROM queue_jobs WHERE id = ?1",
        )?;
        let mut rows = statement.query([id])?;
        if let Some(row) = rows.next()? {
            Ok(Some(row_to_queue_job(row)?))
        } else {
            Ok(None)
        }
    }

    fn list_queue_jobs(&self) -> Result<Vec<QueueJobRecord>> {
        let mut statement = self.connection.prepare(
            "SELECT id, queue_id, url, output_dir, file_name, category, connections, expected_sha256_hex, headers_json, referrer, cookies, user_agent, username, password, proxy_url, proxy_username, proxy_password, status, priority, queue_order, attempt_count, last_error FROM queue_jobs ORDER BY queue_id ASC, priority DESC, queue_order ASC, created_at ASC",
        )?;
        let rows = statement.query_map([], row_to_queue_job)?;
        let mut jobs = Vec::new();
        for row in rows {
            jobs.push(row?);
        }
        Ok(jobs)
    }

    fn list_queue_view_rows(&self) -> Result<Vec<QueueViewRow>> {
        let mut statement = self.connection.prepare(
            "
            SELECT
                q.id,
                q.queue_id,
                g.name,
                q.file_name,
                q.status,
                q.attempt_count,
                COALESCE(d.downloaded_bytes, 0) AS downloaded_bytes,
                d.total_bytes,
                q.last_error,
                q.created_at
            FROM queue_jobs q
            LEFT JOIN queue_groups g ON g.id = q.queue_id
            LEFT JOIN downloads d ON d.id = q.id
            ORDER BY q.queue_id ASC, q.priority DESC, q.created_at ASC
            ",
        )?;
        let rows = statement.query_map([], |row| {
            Ok(QueueViewRow {
                id: row.get(0)?,
                queue_id: row.get::<_, Option<i64>>(1)?.unwrap_or(0),
                queue_name: row.get::<_, Option<String>>(2)?.unwrap_or_else(|| "Main".to_string()),
                file_name: row.get(3)?,
                status: row.get(4)?,
                attempt_count: row.get(5)?,
                downloaded_bytes: row.get::<_, i64>(6)?.max(0) as u64,
                total_bytes: row.get::<_, Option<i64>>(7)?.map(|v| v.max(0) as u64),
                last_error: row.get(8)?,
                created_at: row.get::<_, Option<i64>>(9)?.unwrap_or_default(),
            })
        })?;

        let mut out = Vec::new();
        for row in rows {
            out.push(row?);
        }
        Ok(out)
    }

    fn reset_queue_job_for_retry(&self, id: &str) -> Result<()> {
        self.connection.execute("DELETE FROM download_chunks WHERE download_id = ?1", [id])?;
        self.connection.execute("DELETE FROM downloads WHERE id = ?1", [id])?;
        self.connection.execute(
            "UPDATE queue_jobs SET status = 'Queued', attempt_count = 0, last_error = NULL, updated_at = unixepoch() WHERE id = ?1",
            [id],
        )?;
        Ok(())
    }

    fn cleanup_queue_jobs_by_status(&self, statuses: &[String]) -> Result<usize> {
        let mut removed = 0;
        for status in statuses {
            let mut statement = self.connection.prepare("SELECT id FROM queue_jobs WHERE status = ?1")?;
            let ids = statement
                .query_map([status], |row| row.get::<_, String>(0))?
                .collect::<Result<Vec<_>>>()?;
            for id in ids {
                removed += usize::from(self.delete_download_job(&id).is_ok());
            }
        }
        Ok(removed)
    }

    fn delete_download_job(&self, id: &str) -> Result<()> {
        self.connection.execute("DELETE FROM download_chunks WHERE download_id = ?1", [id])?;
        self.connection.execute("DELETE FROM downloads WHERE id = ?1", [id])?;
        self.connection.execute("DELETE FROM queue_jobs WHERE id = ?1", [id])?;
        Ok(())
    }

    fn reorder_queue_job(&self, id: &str, direction: i64) -> Result<()> {
        let current = self.get_queue_job(id)?;
        let Some(current) = current else { return Ok(()); };

        let mut statement = self.connection.prepare(
            "
            SELECT id, queue_order
            FROM queue_jobs
            WHERE queue_id = ?1
            ORDER BY priority DESC, queue_order ASC, created_at ASC
            ",
        )?;
        let ordered = statement
            .query_map([current.queue_id], |row| {
                Ok((row.get::<_, String>(0)?, row.get::<_, i64>(1)?))
            })?
            .collect::<Result<Vec<_>>>()?;

        let Some(current_index) = ordered.iter().position(|(job_id, _)| job_id == &current.id) else {
            return Ok(());
        };
        let target_index = if direction < 0 {
            current_index.saturating_sub(1)
        } else {
            (current_index + 1).min(ordered.len().saturating_sub(1))
        };
        if current_index == target_index {
            return Ok(());
        }

        let (other_id, other_order) = &ordered[target_index];
        self.connection.execute("UPDATE queue_jobs SET queue_order = ?2 WHERE id = ?1", (&current.id, *other_order))?;
        self.connection.execute("UPDATE queue_jobs SET queue_order = ?2 WHERE id = ?1", (other_id, current.queue_order))?;
        normalize_queue_order(&self.connection, current.queue_id)?;
        Ok(())
    }

    fn push_queue_job_to_end(&self, id: &str) -> Result<()> {
        let current = self.get_queue_job(id)?;
        let Some(current) = current else { return Ok(()); };
        let max_order: i64 = self.connection.query_row(
            "SELECT COALESCE(MAX(queue_order), 0) FROM queue_jobs WHERE queue_id = ?1",
            [current.queue_id],
            |row| row.get(0),
        )?;
        self.connection.execute(
            "UPDATE queue_jobs SET queue_order = ?2, updated_at = unixepoch() WHERE id = ?1",
            (id, max_order + 1),
        )?;
        normalize_queue_order(&self.connection, current.queue_id)?;
        Ok(())
    }

    fn move_queue_job_to_index(&self, id: &str, target_index: usize) -> Result<()> {
        let current = self.get_queue_job(id)?;
        let Some(current) = current else { return Ok(()); };

        let mut statement = self.connection.prepare(
            "
            SELECT id
            FROM queue_jobs
            WHERE queue_id = ?1
            ORDER BY priority DESC, queue_order ASC, created_at ASC
            ",
        )?;
        let mut ordered = statement
            .query_map([current.queue_id], |row| row.get::<_, String>(0))?
            .collect::<Result<Vec<_>>>()?;

        let Some(current_index) = ordered.iter().position(|job_id| job_id == &current.id) else {
            return Ok(());
        };
        let target = target_index.min(ordered.len().saturating_sub(1));
        if current_index == target {
            return Ok(());
        }

        let item = ordered.remove(current_index);
        ordered.insert(target, item);
        for (index, job_id) in ordered.into_iter().enumerate() {
            self.connection.execute(
                "UPDATE queue_jobs SET queue_order = ?2 WHERE id = ?1",
                (job_id, index as i64),
            )?;
        }
        Ok(())
    }

    fn list_recoverable_jobs(&self) -> Result<Vec<QueueJobRecord>> {
        let mut statement = self.connection.prepare(
            "
            SELECT id, queue_id, url, output_dir, file_name, connections, expected_sha256_hex, headers_json, referrer, cookies, user_agent, username, password, proxy_url, proxy_username, proxy_password, status, priority, queue_order, attempt_count, last_error
            FROM queue_jobs
            WHERE status IN ('Queued', 'Downloading')
            ORDER BY queue_id ASC, priority DESC, queue_order ASC, created_at ASC
            ",
        )?;
        let rows = statement.query_map([], row_to_queue_job)?;

        let mut jobs = Vec::new();
        for row in rows {
            jobs.push(row?);
        }
        Ok(jobs)
    }
}

fn row_to_queue_job(row: &rusqlite::Row<'_>) -> Result<QueueJobRecord> {
    let connections: i64 = row.get(6)?;
    Ok(QueueJobRecord {
        id: row.get(0)?,
        queue_id: row.get(1)?,
        url: row.get(2)?,
        output_dir: row.get(3)?,
        file_name: row.get(4)?,
        category: row.get::<_, Option<String>>(5)?.unwrap_or_else(|| "General".to_string()),
        connections: connections.max(1) as usize,
        expected_sha256_hex: row.get(7)?,
        headers_json: row.get(8)?,
        referrer: row.get(9)?,
        cookies: row.get(10)?,
        user_agent: row.get(11)?,
        username: row.get(12)?,
        password: row.get(13)?,
        proxy_url: row.get(14)?,
        proxy_username: row.get(15)?,
        proxy_password: row.get(16)?,
        status: row.get(17)?,
        priority: row.get(18)?,
        queue_order: row.get(19)?,
        attempt_count: row.get(20)?,
        last_error: row.get(21)?,
    })
}

fn add_optional_column(connection: &Connection, table: &str, column: &str, definition: &str) -> Result<()> {
    let existing = format!("SELECT {column} FROM {table} LIMIT 0");
    if connection.prepare(&existing).is_ok() {
        return Ok(());
    }
    connection.execute(&format!("ALTER TABLE {table} ADD COLUMN {column} {definition}"), [])?;
    Ok(())
}

fn normalize_queue_order(connection: &Connection, queue_id: i64) -> Result<()> {
    let mut statement = connection.prepare(
        "
        SELECT id
        FROM queue_jobs
        WHERE queue_id = ?1
        ORDER BY priority DESC, queue_order ASC, created_at ASC
        ",
    )?;
    let ids = statement
        .query_map([queue_id], |row| row.get::<_, String>(0))?
        .collect::<Result<Vec<_>>>()?;
    for (index, id) in ids.into_iter().enumerate() {
        connection.execute(
            "UPDATE queue_jobs SET queue_order = ?2 WHERE id = ?1",
            (id, index as i64),
        )?;
    }
    Ok(())
}
