//! Dependency-free host fixture to verify real Cargo execution in CI.

pub fn add(a: i32, b: i32) -> i32 { a + b }

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn adds_two_values() { assert_eq!(add(2, 3), 5); }
}
