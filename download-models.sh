#!/usr/bin/env bash

set -euo pipefail

# ========================
# Configuration variables
# ========================

# Embed model (retriever)
embed_model_onnx_url="https://huggingface.co/intfloat/multilingual-e5-large/resolve/main/onnx/model.onnx?download=true"
embed_model_onnx_data_url="https://huggingface.co/intfloat/multilingual-e5-large/resolve/main/onnx/model.onnx_data?download=true"
embed_model_tokenizer_url="https://huggingface.co/intfloat/multilingual-e5-large/resolve/main/tokenizer.json?download=true"

embed_model_onnx_md5="507240d227c1071b8a8e5b4f296bccf1"
embed_model_onnx_data_md5="c6c72edbdef762ae9d15fd1681415f41"
embed_model_tokenizer_md5="3f3e3d8ef57e1142838e20f8f819acd3"

# Rerank model
rerank_model_onnx_url="https://huggingface.co/jinaai/jina-reranker-v2-base-multilingual/resolve/main/onnx/model.onnx?download=true"
rerank_model_tokenizer_url="https://huggingface.co/jinaai/jina-reranker-v2-base-multilingual/resolve/main/tokenizer.json?download=true"
rerank_model_onnx_md5="11dd67a87534498a76554b8d833c5f67"
rerank_model_tokenizer_md5="ca92eea47faffaa2a3a996a2f74879e8"

# ========================
# Helper functions
# ========================

download_and_check() {
  local url="$1"
  local dest="$2"
  local expected_md5="$3"

  echo "Downloading $url → $dest"
  curl -L -o "$dest" "$url"

  echo "Verifying checksum for $dest"
  local actual_md5
  actual_md5=$(md5 -q "$dest" 2>/dev/null || md5sum "$dest" | awk '{print $1}')

  if [[ "$actual_md5" != "$expected_md5" ]]; then
    echo "❌ MD5 mismatch for $dest"
    echo "Expected: $expected_md5"
    echo "Actual:   $actual_md5"
    exit 1
  fi

  echo "✅ $dest checksum OK"
}

# ========================
# Main script
# ========================

base_dir="${HOME}/.docqa/models"
retriever_dir="${base_dir}/retriever"
reranker_dir="${base_dir}/reranker"

echo "Initializing model directories under $base_dir"
mkdir -p "$retriever_dir" "$reranker_dir"

# Download embedder (retriever) files
download_and_check "$embed_model_onnx_url" "$retriever_dir/model.onnx" "$embed_model_onnx_md5"
download_and_check "$embed_model_onnx_data_url" "$retriever_dir/model.onnx_data" "$embed_model_onnx_data_md5"
download_and_check "$embed_model_tokenizer_url" "$retriever_dir/tokenizer.json" "$embed_model_tokenizer_md5"

# Download reranker files
download_and_check "$rerank_model_onnx_url" "$reranker_dir/model.onnx" "$rerank_model_onnx_md5"
download_and_check "$rerank_model_tokenizer_url" "$reranker_dir/tokenizer.json" "$rerank_model_tokenizer_md5"

echo "🎉 All models downloaded and verified successfully."
