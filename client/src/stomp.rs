//! Minimal STOMP 1.2 framing over a raw WebSocket text stream — just enough to
//! CONNECT, SUBSCRIBE, and receive MESSAGE frames from Spring's STOMP broker.
//! Not a general-purpose STOMP client (no ack modes, transactions, etc.).

pub fn connect_frame() -> String {
    frame("CONNECT", &[("accept-version", "1.2"), ("heart-beat", "10000,10000")], "")
}

pub fn subscribe_frame(id: &str, destination: &str) -> String {
    frame(
        "SUBSCRIBE",
        &[("id", id), ("destination", destination)],
        "",
    )
}

fn frame(command: &str, headers: &[(&str, &str)], body: &str) -> String {
    let mut out = String::new();
    out.push_str(command);
    out.push('\n');
    for (key, value) in headers {
        out.push_str(key);
        out.push(':');
        out.push_str(value);
        out.push('\n');
    }
    out.push('\n');
    out.push_str(body);
    out.push('\0');
    out
}

/// A parsed STOMP frame received from the server.
pub struct StompFrame {
    pub command: String,
    pub destination: Option<String>,
    pub body: String,
}

/// Parses one STOMP frame (as delivered in a single WebSocket text message by
/// Spring's SockJS-less raw STOMP endpoint). Returns None for heartbeat
/// newlines or malformed frames.
pub fn parse_frame(raw: &str) -> Option<StompFrame> {
    let raw = raw.trim_end_matches('\0');
    if raw.trim().is_empty() {
        return None; // heartbeat
    }

    let mut parts = raw.splitn(2, "\n\n");
    let header_block = parts.next()?;
    let body = parts.next().unwrap_or("").to_string();

    let mut lines = header_block.lines();
    let command = lines.next()?.to_string();

    let mut destination = None;
    for line in lines {
        if let Some((key, value)) = line.split_once(':') {
            if key == "destination" {
                destination = Some(value.to_string());
            }
        }
    }

    Some(StompFrame {
        command,
        destination,
        body,
    })
}
