-- ==============================================================================
-- 1. MÓDULO DE AUTENTICAÇÃO E PERMISSÕES (ACL)
-- ==============================================================================

-- Tabela Base de Usuários
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    is_global_admin BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Tabela Central de Categorias (Lista oficial do que existe no sistema)
CREATE TABLE categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) UNIQUE NOT NULL,       -- Ex: 'Mulher', 'Município'
    entity_type VARCHAR(50) NOT NULL,        -- Define se é 'native' ou 'dynamic'
    table_reference VARCHAR(100) NOT NULL,   -- Tabela no banco (ex: 'mulheres' ou 'dynamic_entities')
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Matriz de Permissões (Controle ultra-granular por usuário e por coluna)
CREATE TABLE permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    can_read BOOLEAN DEFAULT FALSE,
    can_create BOOLEAN DEFAULT FALSE,
    can_update BOOLEAN DEFAULT FALSE,
    can_delete BOOLEAN DEFAULT FALSE,
    -- JSONB para dizer quais colunas o usuário pode ver/editar. 
    -- Ex: {"read": ["nome", "telefone", "sob_risco"], "write": ["telefone"]}
    allowed_columns JSONB DEFAULT '{}'::jsonb, 
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, category_id) -- Impede permissões duplicadas para o mesmo usuário na mesma categoria
);


-- ==============================================================================
-- 2. MÓDULO DE ENTIDADES (O CORAÇÃO MODULAR)
-- ==============================================================================

-- Tabela de Categoria Nativa: Clientes (Atendidas / Mulheres)
-- (Alta performance, regras de negócio rígidas)
CREATE TABLE clients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome VARCHAR(255) NOT NULL,
    data_nascimento DATE,
    telefone VARCHAR(50),
    sob_risco BOOLEAN DEFAULT FALSE,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Tabela de Categorias Genéricas (Dinâmicas)
CREATE TABLE dynamic_entities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE RESTRICT,
    custom_fields JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ==============================================================================
-- 3. MÓDULO DE HISTÓRICO / LEDGER
-- ==============================================================================

-- Diário Universal e Imutável de Atividades
CREATE TABLE activities_ledger (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Polimorfismo: Qual tabela? (Ex: 'mulheres' ou 'dynamic_entities')
    entity_type VARCHAR(100) NOT NULL, 
    -- Polimorfismo: Qual registro específico dentro da tabela?
    entity_id UUID NOT NULL, 
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    
    -- Ex: 'MANUAL_ENTRY' (Atendimento anotado) ou 'AUTO_UPDATE' (Sistema auditando alteração)
    action_type VARCHAR(50) NOT NULL, 
    
    -- Texto livre do registro da atividade
    description TEXT, 
    
    -- O "Ledger" para auditoria de segurança (O que mudou)
    -- Ex: {"campo": "telefone", "antigo": "1111", "novo": "2222"}
    changes JSONB, 
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Índices de performance cruciais para tabelas polimórficas gigantes
CREATE INDEX idx_ledger_polymorphic ON activities_ledger(entity_type, entity_id);
CREATE INDEX idx_ledger_user ON activities_ledger(user_id);


-- ==============================================================================
-- 4. MÓDULO DE ARQUIVOS CENTRALIZADO
-- ==============================================================================

-- Repositório Único de Documentos e Anexos
CREATE TABLE files (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Polimorfismo (Idêntico ao do Ledger)
    entity_type VARCHAR(100) NOT NULL, 
    entity_id UUID NOT NULL, 
    
    uploaded_by UUID REFERENCES users(id) ON DELETE SET NULL,
    
    -- Metadados do arquivo
    original_name VARCHAR(255) NOT NULL,
    storage_path VARCHAR(500) NOT NULL, -- Caminho no S3, GCP, ou disco local
    file_size_bytes BIGINT,
    
    uploaded_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Índice para buscas rápidas ao abrir a pasta de anexos de um registro
CREATE INDEX idx_files_polymorphic ON files(entity_type, entity_id);