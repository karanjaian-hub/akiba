FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY pdf_helper.py .

EXPOSE 8099

HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8099/health')" || exit 1

CMD ["uvicorn", "pdf_helper:app", "--host", "0.0.0.0", "--port", "8099"]
