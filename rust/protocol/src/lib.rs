//! Decodificacao pura e testavel de bytes recebidos (nao escreve firmware).
//! Moyoung V2: estrutura parcial. Comandos especificos ficam desconhecidos ate validacao.
#[derive(Debug, PartialEq, Eq)]
pub enum DecodeError { Short, InvalidHeader, InvalidLength }

#[derive(Debug, PartialEq, Eq)]
pub struct MoyoungFrame<'a> { pub declared_len: usize, pub command: u8, pub payload: &'a [u8] }

pub fn parse_moyoung_v2(bytes: &[u8]) -> Result<MoyoungFrame<'_>, DecodeError> {
    if bytes.len() < 5 { return Err(DecodeError::Short); }
    if bytes[0] != 0xfe || bytes[1] != 0xea { return Err(DecodeError::InvalidHeader); }
    let declared_len = ((bytes[2] as usize) << 8) | bytes[3] as usize;
    // Tamanho nos pacotes da captura: 0x2008 e 0x2007 sao campos codificados,
    // NAO comprimentos literais de 0x2008 bytes. Nao interpretar erroneamente.
    Ok(MoyoungFrame { declared_len, command: bytes[4], payload: &bytes[5..] })
}

pub fn parse_standard_hr(bytes: &[u8]) -> Result<u16, DecodeError> {
    if bytes.len() < 2 { return Err(DecodeError::Short); }
    if bytes[0] & 1 == 0 { Ok(bytes[1] as u16) }
    else if bytes.len() >= 3 { Ok(u16::from_le_bytes([bytes[1], bytes[2]])) }
    else { Err(DecodeError::Short) }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test] fn hr_8_bits() { assert_eq!(parse_standard_hr(&[0x06, 0x43]), Ok(67)); }
    #[test] fn hr_16_bits() { assert_eq!(parse_standard_hr(&[0x01, 0x2c, 0x01]), Ok(300)); }
    #[test] fn unknown_moyoung_command() {
        let raw = [0xfe,0xea,0x20,0x08,0xb9,0x11,0x00,0xff];
        let frame = parse_moyoung_v2(&raw).unwrap();
        assert_eq!(frame.command, 0xb9);
        assert_eq!(frame.payload, &[0x11,0x00,0xff]);
    }
}
