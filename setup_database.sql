-- 1. Create the Database
CREATE DATABASE IF NOT EXISTS bank_system;

-- 2. Select the Database
USE bank_system;

-- 3. Create the Users Table
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(12) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    password VARCHAR(100) NOT NULL,
    balance INT DEFAULT 0
);

-- 4. (Optional) Insert Test Data
-- Alice (ID: 100000000001, Pass: 1234, Balance: 1000)
INSERT INTO users (id, name, password, balance) VALUES 
('100000000001', 'Alice Smith', '1234', 1000)
ON DUPLICATE KEY UPDATE balance=balance;

-- Bob (ID: 100000000002, Pass: 5678, Balance: 500)
INSERT INTO users (id, name, password, balance) VALUES 
('100000000002', 'Bob Jones', '5678', 500)
ON DUPLICATE KEY UPDATE balance=balance;
