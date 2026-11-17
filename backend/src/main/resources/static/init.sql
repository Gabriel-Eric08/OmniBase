-- ==============================================================================
-- INICIALIZAÇÃO E EXTENSÕES
-- ==============================================================================

-- Garante que a função gen_random_uuid() esteja disponível no banco de dados
CREATE EXTENSION IF NOT EXISTS pgcrypto;


-- ==============================================================================
-- 1. MÓDULO DE AUTENTICAÇÃO E PERMISSÕES (ACL)
-- ==============================================================================

-- Tabela Base de Usuários
CREATE TABLE IF NOT EXISTS users (
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
CREATE TABLE IF NOT EXISTS categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) UNIQUE NOT NULL,       -- Ex: 'Mulher', 'Município'
    entity_type VARCHAR(50) NOT NULL,        -- Define se é 'native' ou 'dynamic'
    table_reference VARCHAR(100) NOT NULL,   -- Tabela no banco (ex: 'clients' ou 'dynamic_entities')
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Matriz de Permissões (Controle ultra-granular por usuário e por coluna)
CREATE TABLE IF NOT EXISTS permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    can_read BOOLEAN DEFAULT FALSE,
    can_create BOOLEAN DEFAULT FALSE,
    can_update BOOLEAN DEFAULT FALSE,
    can_delete BOOLEAN DEFAULT FALSE,
    -- JSONB para dizer quais colunas o usuário pode ver/editar. 
    -- Ex: {"read": ["nome", "telefone", "sob_risco"], "write": ["telefone"]}
    -- Sem a chave "read"/"write", todas as colunas são liberadas para aquela operação.
    allowed_columns JSONB DEFAULT '{}'::jsonb,
    -- Nível máximo de sigilo de documento acessível: INTERNAL, RESTRICTED ou CONFIDENTIAL
    max_confidentiality VARCHAR(20) NOT NULL DEFAULT 'INTERNAL',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, category_id) -- Impede permissões duplicadas para o mesmo usuário na mesma categoria
);


-- ==============================================================================
-- 2. MÓDULO DE ENTIDADES (O CORAÇÃO MODULAR)
-- ==============================================================================

-- Tabela de Categoria Nativa: Clientes (Atendidas / Mulheres)
-- (Alta performance, regras de negócio rígidas)
CREATE TABLE IF NOT EXISTS clients (
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
CREATE TABLE IF NOT EXISTS dynamic_entities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE RESTRICT,
    custom_fields JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Índices para listagem por categoria e buscas por conteúdo (operador @>)
CREATE INDEX IF NOT EXISTS idx_dynamic_entities_category ON dynamic_entities(category_id);
CREATE INDEX IF NOT EXISTS idx_dynamic_entities_custom_fields ON dynamic_entities USING GIN (custom_fields);

-- Definição dos campos customizáveis de cada categoria dinâmica (o "formulário")
CREATE TABLE IF NOT EXISTS category_fields (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    field_key VARCHAR(100) NOT NULL,         -- Chave usada no JSONB (ex: 'bairro')
    label VARCHAR(255) NOT NULL,             -- Rótulo exibido (ex: 'Bairro')
    field_type VARCHAR(20) NOT NULL,         -- TEXT, NUMBER, DATE, BOOLEAN, LIST
    is_required BOOLEAN DEFAULT FALSE,
    options JSONB,                           -- Opções válidas quando field_type = 'LIST'
    position INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(category_id, field_key)
);


-- ==============================================================================
-- 3. MÓDULO DE HISTÓRICO / LEDGER
-- ==============================================================================

-- Diário Universal e Imutável de Atividades
CREATE TABLE IF NOT EXISTS activities_ledger (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Polimorfismo: Qual tabela? (Ex: 'clients' ou 'dynamic_entities')
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

    -- Funcionário que realizou o atendimento (pode não ser usuário do sistema)
    responsible_employee VARCHAR(255),

    -- Quando a atividade aconteceu (pode ser anterior ao registro no sistema)
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Índices de performance cruciais para tabelas polimórficas gigantes
CREATE INDEX IF NOT EXISTS idx_ledger_polymorphic ON activities_ledger(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_ledger_user ON activities_ledger(user_id);

-- Tipos de registro manual configuráveis por categoria (ex: 'Atendimento psicológico', 'Matrícula')
CREATE TABLE IF NOT EXISTS activity_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    key VARCHAR(100) NOT NULL,               -- Identificador estável (^[a-z][a-z0-9_]*$), não muda depois de criado
    label VARCHAR(255) NOT NULL,             -- Nome exibido
    description TEXT,
    color VARCHAR(20),                       -- Ex: '#5b2a86'
    position INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(category_id, key)
);

-- Registros manuais apontam para o tipo e guardam o nome dele no momento do registro (auditoria imutável)
ALTER TABLE activities_ledger
    ADD COLUMN IF NOT EXISTS activity_type_id UUID REFERENCES activity_types(id) ON DELETE RESTRICT;
ALTER TABLE activities_ledger ADD COLUMN IF NOT EXISTS activity_type_label VARCHAR(255);
CREATE INDEX IF NOT EXISTS idx_ledger_activity_type ON activities_ledger(activity_type_id);

-- Tipos que o usuário pode registrar na categoria (lista de keys); NULL = todos
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS allowed_activity_types JSONB;

-- Migração (idempotente): os quatro tipos padrão em cada categoria que ainda não tem nenhum tipo
INSERT INTO activity_types (category_id, key, label, position)
SELECT c.id, d.key, d.label, d.position
FROM categories c
CROSS JOIN (VALUES ('triagem', 'Triagem', 0), ('atendimento', 'Atendimento', 1),
                   ('acompanhamento', 'Acompanhamento', 2), ('anotacao', 'Anotação', 3)) AS d(key, label, position)
WHERE NOT EXISTS (SELECT 1 FROM activity_types t WHERE t.category_id = c.id);

-- Migração (idempotente): registros manuais antigos (enum fixo) passam a apontar para o tipo da categoria
-- do registro. Os que não têm categoria identificável (ex: registro já excluído) continuam como estão.
UPDATE activities_ledger l
SET action_type = 'MANUAL', activity_type_id = t.id, activity_type_label = t.label
FROM activity_types t
WHERE l.action_type IN ('TRIAGE', 'SERVICE', 'FOLLOW_UP', 'NOTE')
  AND t.key = CASE l.action_type
                  WHEN 'TRIAGE' THEN 'triagem'
                  WHEN 'SERVICE' THEN 'atendimento'
                  WHEN 'FOLLOW_UP' THEN 'acompanhamento'
                  ELSE 'anotacao'
              END
  AND t.category_id = CASE l.entity_type
                          WHEN 'clients' THEN (SELECT c.id FROM categories c
                                               WHERE c.table_reference = 'clients' ORDER BY c.created_at LIMIT 1)
                          WHEN 'dynamic_entities' THEN (SELECT e.category_id FROM dynamic_entities e
                                                        WHERE e.id = l.entity_id)
                      END;


-- ==============================================================================
-- 4. MÓDULO DE ARQUIVOS CENTRALIZADO
-- ==============================================================================

-- Repositório Único de Documentos e Anexos
CREATE TABLE IF NOT EXISTS files (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Polimorfismo (Idêntico ao do Ledger)
    entity_type VARCHAR(100) NOT NULL, 
    entity_id UUID NOT NULL, 
    
    uploaded_by UUID REFERENCES users(id) ON DELETE SET NULL,
    
    -- Metadados do arquivo
    original_name VARCHAR(255) NOT NULL,
    storage_path VARCHAR(500) NOT NULL, -- Caminho no S3, GCP, ou disco local
    file_size_bytes BIGINT,
    content_type VARCHAR(255),

    -- Nível de sigilo: INTERNAL (público interno), RESTRICTED (restrito), CONFIDENTIAL (sigiloso)
    confidentiality_level VARCHAR(20) NOT NULL DEFAULT 'RESTRICTED',

    uploaded_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Índice para buscas rápidas ao abrir a pasta de anexos de um registro
CREATE INDEX IF NOT EXISTS idx_files_polymorphic ON files(entity_type, entity_id);