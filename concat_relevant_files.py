#!/usr/bin/env python3
"""
Concatena arquivos relevantes do projeto em um único arquivo de texto,
com separadores por nome de arquivo.

Uso:
  python3 concat_relevant_files.py
  python3 concat_relevant_files.py --output dump.txt
  python3 concat_relevant_files.py --most-relevant-files app/src/main/java/A.java scripts/x.sh
  python3 concat_relevant_files.py --all-relevant
"""

from __future__ import annotations

import argparse
from pathlib import Path

ROOT = Path(__file__).resolve().parent

INCLUDE_EXTENSIONS = {
    ".java",
    ".py",
    ".xml",
    ".yml",
    ".yaml",
    ".json",
    ".md",
    ".sh",
    ".cfg",
    ".properties",
    ".txt",
    ".gitignore",
}

INCLUDE_EXACT_NAMES = {
    "Dockerfile",
    "docker-compose.yml",
    "docker-compose.yaml",
    "pom.xml",
    "mvnw",
    "mvnw.cmd",
}

EXCLUDE_DIRS = {
    ".git",
    ".idea",
    ".vscode",
    "target",
    "build",
    "out",
    "node_modules",
    "__pycache__",
    ".mvn",
}

EXCLUDE_FILES = {
    ".codex",
    "fraud-index.dat",
    "references.json.gz",
}

DEFAULT_MOST_RELEVANT_FILES = [
    "app/src/main/java/com/expedit/rinha2026/search/ExactKnnSearchEngine.java",
    "app/src/main/java/com/expedit/rinha2026/parser/PayloadParser.java",
    "app/src/main/java/com/expedit/rinha2026/http/FraudScoreHandler.java",
    "app/src/main/java/com/expedit/rinha2026/http/HttpServerBootstrap.java",
    "app/src/main/java/com/expedit/rinha2026/vectorizer/FraudVectorizer.java",
    "app/src/main/java/com/expedit/rinha2026/vectorizer/MinutesSinceLastTxCalculator.java",
    "app/src/main/java/com/expedit/rinha2026/vectorizer/DayOfWeekCalculator.java",
    "app/src/main/java/com/expedit/rinha2026/search/DistanceKernel.java",
    "app/src/main/java/com/expedit/rinha2026/search/Top5Selector.java",
    "app/src/main/java/com/expedit/rinha2026/bootstrap/StartupLoader.java",
    "tools/preprocessor/src/main/java/com/expedit/rinha2026/tools/preprocessor/PreprocessorMain.java",
    "tools/preprocessor/src/main/java/com/expedit/rinha2026/tools/preprocessor/BucketBoundsCalculator.java",
    "tools/preprocessor/src/main/java/com/expedit/rinha2026/tools/preprocessor/BucketKeyEncoder.java",
    "app/src/main/java/com/expedit/rinha2026/infra/index/BinaryIndexLoader.java",
    "app/src/main/java/com/expedit/rinha2026/infra/index/LoadedIndex.java",
    "benchmark/docker-compose.local.yml",
    "benchmark/haproxy.cfg",
    "submission/docker-compose.yml",
    "scripts/verify-rules.sh",
    "benchmark/run-k6.sh",
]


def is_relevant_file(path: Path) -> bool:
    if not path.is_file():
        return False

    if path.name in EXCLUDE_FILES:
        return False

    if any(part in EXCLUDE_DIRS for part in path.parts):
        return False

    if path.name in INCLUDE_EXACT_NAMES:
        return True

    suffix = path.suffix.lower()
    if suffix in INCLUDE_EXTENSIONS:
        return True

    return False


def build_separator(rel_path: Path) -> str:
    name = rel_path.as_posix()
    return f"\n{'=' * 30} {name} {'=' * 30}\n"


def collect_files(root: Path) -> list[Path]:
    files = []
    for p in root.rglob("*"):
        if is_relevant_file(p):
            files.append(p)
    files.sort(key=lambda x: x.relative_to(root).as_posix())
    return files


def resolve_most_relevant_files(root: Path, raw_paths: list[str]) -> list[Path]:
    selected: list[Path] = []
    seen: set[Path] = set()

    for raw_path in raw_paths:
        path = Path(raw_path)
        resolved = path.resolve() if path.is_absolute() else (root / path).resolve()

        try:
            resolved.relative_to(root)
        except ValueError as exc:
            raise ValueError(f"Arquivo fora do projeto: {raw_path}") from exc

        if not resolved.exists() or not resolved.is_file():
            raise FileNotFoundError(f"Arquivo não encontrado: {raw_path}")

        if resolved in seen:
            continue
        seen.add(resolved)
        selected.append(resolved)

    return selected


def concat_files(root: Path, output_path: Path, files: list[Path] | None = None) -> None:
    files_to_concat = collect_files(root) if files is None else files

    with output_path.open("w", encoding="utf-8") as out:
        out.write(f"Projeto: {root}\n")
        out.write(f"Total de arquivos: {len(files_to_concat)}\n")

        for file_path in files_to_concat:
            rel = file_path.relative_to(root)
            out.write(build_separator(rel))
            try:
                content = file_path.read_text(encoding="utf-8")
            except UnicodeDecodeError:
                out.write("[arquivo ignorado: conteúdo não UTF-8]\n")
                continue
            out.write(content)
            if not content.endswith("\n"):
                out.write("\n")

    print(f"Arquivo gerado: {output_path}")
    print(f"Arquivos concatenados: {len(files_to_concat)}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Concatena arquivos relevantes do projeto")
    parser.add_argument(
        "--output",
        "-o",
        default="project_concat.txt",
        help="Caminho do arquivo de saída (padrão: project_concat.txt)",
    )
    parser.add_argument(
        "--most-relevant-files",
        nargs="*",
        default=None,
        help="Lista de arquivos (na ordem desejada). Se usado sem valores, aplica a lista padrão de arquivos críticos.",
    )
    parser.add_argument(
        "--all-relevant",
        action="store_true",
        help="Concatena todos os arquivos relevantes detectados automaticamente.",
    )
    args = parser.parse_args()

    output_path = (ROOT / args.output).resolve()
    if args.all_relevant:
        concat_files(ROOT, output_path)
        return

    if args.most_relevant_files is not None:
        selected = args.most_relevant_files if args.most_relevant_files else DEFAULT_MOST_RELEVANT_FILES
        files = resolve_most_relevant_files(ROOT, selected)
        concat_files(ROOT, output_path, files)
        return

    files = resolve_most_relevant_files(ROOT, DEFAULT_MOST_RELEVANT_FILES)
    concat_files(ROOT, output_path, files)


if __name__ == "__main__":
    main()
