import base64
import fitz  # PyMuPDF
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

app = FastAPI(title="pdf-helper")


class ExtractRequest(BaseModel):
    pdf: str  # base64-encoded PDF bytes


class ExtractResponse(BaseModel):
    text: str
    page_count: int


@app.post("/extract", response_model=ExtractResponse)
def extract_text(request: ExtractRequest):
    try:
        pdf_bytes = base64.b64decode(request.pdf)
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid base64 content")

    try:
        # fitz.open with stream= reads from bytes directly, no temp file needed
        doc = fitz.open(stream=pdf_bytes, filetype="pdf")
    except Exception as e:
        raise HTTPException(status_code=422, detail=f"Could not open PDF: {e}")

    # Extract text from every page and join with newlines
    pages_text = [page.get_text() for page in doc]
    full_text  = "\n".join(pages_text).strip()
    doc.close()

    if not full_text:
        raise HTTPException(status_code=422, detail="PDF appears to be image-only — no extractable text found")

    return ExtractResponse(text=full_text, page_count=len(pages_text))


@app.get("/health")
def health():
    return {"status": "UP", "service": "pdf-helper"}
