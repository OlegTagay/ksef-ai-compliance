"""
Logging Configuration for KSeF Invoice Processing

Provides structured logging with JSON format, log rotation, and correlation IDs.
"""

import logging
import logging.handlers
import json
import sys
import uuid
from datetime import datetime
from pathlib import Path
from typing import Optional
import contextvars

# Context variable for correlation ID (thread-safe)
correlation_id_var = contextvars.ContextVar('correlation_id', default=None)


class JSONFormatter(logging.Formatter):
    """Custom JSON formatter for structured logging."""

    def format(self, record: logging.LogRecord) -> str:
        """Format log record as JSON."""
        log_data = {
            'timestamp': datetime.utcnow().isoformat() + 'Z',
            'level': record.levelname,
            'logger': record.name,
            'message': record.getMessage(),
            'module': record.module,
            'function': record.funcName,
            'line': record.lineno,
        }

        # Add correlation ID if available
        correlation_id = correlation_id_var.get()
        if correlation_id:
            log_data['correlation_id'] = correlation_id

        # Add exception info if present
        if record.exc_info:
            log_data['exception'] = self.formatException(record.exc_info)

        # Add extra fields
        if hasattr(record, 'extra_fields'):
            log_data.update(record.extra_fields)

        return json.dumps(log_data)


class CorrelationIDFilter(logging.Filter):
    """Add correlation ID to log records."""

    def filter(self, record: logging.LogRecord) -> bool:
        """Add correlation_id to the record."""
        correlation_id = correlation_id_var.get()
        if correlation_id:
            record.correlation_id = correlation_id
        return True


def setup_logging(
    log_level: str = "INFO",
    log_file: Optional[str] = None,
    log_format: str = "json",
    max_bytes: int = 10 * 1024 * 1024,  # 10 MB
    backup_count: int = 5
) -> logging.Logger:
    """
    Configure logging for the application.

    Args:
        log_level: Logging level (DEBUG, INFO, WARNING, ERROR, CRITICAL)
        log_file: Path to log file. If None, logs only to console.
        log_format: Format for logs ('json' or 'text')
        max_bytes: Maximum size of log file before rotation
        backup_count: Number of backup files to keep

    Returns:
        Configured logger instance
    """
    # Create logs directory if it doesn't exist
    if log_file:
        log_path = Path(log_file)
        log_path.parent.mkdir(parents=True, exist_ok=True)

    # Get root logger
    logger = logging.getLogger()
    logger.setLevel(getattr(logging, log_level.upper()))

    # Remove existing handlers
    logger.handlers.clear()

    # Create formatters
    if log_format == "json":
        formatter = JSONFormatter()
    else:
        formatter = logging.Formatter(
            fmt='%(asctime)s - %(name)s - %(levelname)s - [%(correlation_id)s] - %(message)s',
            datefmt='%Y-%m-%d %H:%M:%S'
        )

    # Console handler
    console_handler = logging.StreamHandler(sys.stdout)
    console_handler.setLevel(getattr(logging, log_level.upper()))
    console_handler.setFormatter(formatter)
    console_handler.addFilter(CorrelationIDFilter())
    logger.addHandler(console_handler)

    # File handler with rotation (if log_file specified)
    if log_file:
        file_handler = logging.handlers.RotatingFileHandler(
            log_file,
            maxBytes=max_bytes,
            backupCount=backup_count,
            encoding='utf-8'
        )
        file_handler.setLevel(getattr(logging, log_level.upper()))
        file_handler.setFormatter(formatter)
        file_handler.addFilter(CorrelationIDFilter())
        logger.addHandler(file_handler)

    return logger


def get_logger(name: str) -> logging.Logger:
    """
    Get a logger instance with the given name.

    Args:
        name: Logger name (typically __name__)

    Returns:
        Logger instance
    """
    return logging.getLogger(name)


def set_correlation_id(correlation_id: Optional[str] = None) -> str:
    """
    Set correlation ID for the current context.

    Args:
        correlation_id: Correlation ID to set. If None, generates a new UUID.

    Returns:
        The correlation ID that was set
    """
    if correlation_id is None:
        correlation_id = str(uuid.uuid4())
    correlation_id_var.set(correlation_id)
    return correlation_id


def get_correlation_id() -> Optional[str]:
    """
    Get the current correlation ID.

    Returns:
        Current correlation ID or None if not set
    """
    return correlation_id_var.get()


def clear_correlation_id():
    """Clear the correlation ID from the current context."""
    correlation_id_var.set(None)


def log_with_extra(logger: logging.Logger, level: str, message: str, **extra_fields):
    """
    Log a message with extra fields.

    Args:
        logger: Logger instance
        level: Log level (debug, info, warning, error, critical)
        message: Log message
        **extra_fields: Additional fields to include in the log
    """
    log_func = getattr(logger, level.lower())

    # Create a log record with extra fields
    extra = {'extra_fields': extra_fields}
    log_func(message, extra=extra)


# Example usage functions
def log_api_call(logger: logging.Logger, endpoint: str, method: str, status_code: Optional[int] = None, duration_ms: Optional[float] = None):
    """Log an API call with structured data."""
    log_with_extra(
        logger, 'info', f'API call: {method} {endpoint}',
        endpoint=endpoint,
        method=method,
        status_code=status_code,
        duration_ms=duration_ms,
        event_type='api_call'
    )


def log_file_operation(logger: logging.Logger, operation: str, file_path: str, success: bool, error: Optional[str] = None):
    """Log a file operation with structured data."""
    log_with_extra(
        logger, 'info' if success else 'error',
        f'File operation: {operation} {file_path}',
        operation=operation,
        file_path=file_path,
        success=success,
        error=error,
        event_type='file_operation'
    )


def log_invoice_processing(logger: logging.Logger, invoice_number: str, stage: str, success: bool, error: Optional[str] = None):
    """Log invoice processing with structured data."""
    log_with_extra(
        logger, 'info' if success else 'error',
        f'Invoice {invoice_number} - {stage}',
        invoice_number=invoice_number,
        stage=stage,
        success=success,
        error=error,
        event_type='invoice_processing'
    )


# Default configuration
DEFAULT_LOG_LEVEL = "INFO"
DEFAULT_LOG_FORMAT = "json"
DEFAULT_LOG_FILE = "logs/ksef_invoice.log"
