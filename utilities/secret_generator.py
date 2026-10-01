import secrets


def generate_jwt_secrets(count: int = 10) -> list[str]:
    """Generate random JWT secret keys."""
    secrets_list = []

    for _ in range(count):
        secret = secrets.token_urlsafe(32)
        secrets_list.append(secret)

    return secrets_list


if __name__ == "__main__":
    jwt_secrets = generate_jwt_secrets(10)

    print("=== 10 Propozycji Kluczy JWT ===\n")
    for i, secret in enumerate(jwt_secrets, 1):
        print(f"{i}. {secret}")
