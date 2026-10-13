use std::env;

fn from_hex(input: &str) -> Result<Vec<u8>, String> {
    let filtered: String = input.chars().filter(|c| !c.is_ascii_whitespace() && *c != '-').collect();
    if filtered.len() % 2 != 0 { return Err("HEX precisa ter quantidade par de caracteres".into()); }
    (0..filtered.len()).step_by(2).map(|i| u8::from_str_radix(&filtered[i..i+2], 16).map_err(|e| e.to_string())).collect()
}

/// Bluetooth Heart Rate Measurement, especificacao padrao 0x2A37.
fn decode_hr(raw: &[u8]) -> Result<u16, String> {
    if raw.len() < 2 { return Err("Pacote HR muito curto".into()); }
    if raw[0] & 1 == 0 {
        Ok(raw[1] as u16)
    } else if raw.len() >= 3 {
        Ok(u16::from_le_bytes([raw[1], raw[2]]))
    } else {
        Err("HR de 16 bits incompleto".into())
    }
}

#[derive(Debug, PartialEq)]
struct MoyoungHeader { size: usize, command: u8 }

/// Identifica cabecalho sem inventar significado dos comandos proprietarios.
fn decode_moyoung_header(raw: &[u8]) -> Result<MoyoungHeader, String> {
    if raw.len() < 5 || raw[0] != 0xfe || raw[1] != 0xea {
        return Err("Nao e um frame Moyoung V2 valido".into());
    }
    let size = (((raw[2] & 0x1f) as usize) << 8) | (raw[3] as usize);
    if size != raw.len() { return Err(format!("Frame incompleto: cabecalho indica {size}, chegaram {} bytes", raw.len())); }
    Ok(MoyoungHeader { size, command: raw[4] })
}

fn main() {
    let args: Vec<String> = env::args().collect();
    if args.len() != 3 {
        eprintln!("Uso: cargo run -- hr 0643 | cargo run -- moyoung FEEA2008B91100FF");
        std::process::exit(2);
    }
    let bytes = match from_hex(&args[2]) { Ok(v) => v, Err(e) => { eprintln!("{e}"); std::process::exit(1) } };
    let result = match args[1].as_str() {
        "hr" => decode_hr(&bytes).map(|bpm| format!("{{\"bpm\":{bpm}}}")),
        "moyoung" => decode_moyoung_header(&bytes).map(|h| format!("{{\"length\":{},\"command_hex\":\"{:02X}\"}}", h.size, h.command)),
        _ => Err("Modo desconhecido".into()),
    };
    match result { Ok(s) => println!("{s}"), Err(e) => { eprintln!("{e}"); std::process::exit(1) } }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test] fn hr_8bits() { assert_eq!(decode_hr(&from_hex("06-43").unwrap()), Ok(67)); }
    #[test] fn hr_16bits() { assert_eq!(decode_hr(&[0x01, 0x2c, 0x01]), Ok(300)); }
    #[test] fn moyoung_v2() {
        assert_eq!(decode_moyoung_header(&from_hex("FE EA 20 08 B9 11 00 FF").unwrap()), Ok(MoyoungHeader { size: 8, command: 0xb9 }));
    }
    #[test] fn reject_truncated() { assert!(decode_moyoung_header(&[0xfe, 0xea, 0x20, 0x08, 0xb9]).is_err()); }
}
