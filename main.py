import google.generativeai as genai
from fastapi import FastAPI, File, UploadFile, Form
from fastapi.responses import JSONResponse
from typing import List
import torch
from PIL import Image
import re
from transformers import DonutProcessor, VisionEncoderDecoderModel
import io
import json
import typing_extensions as typing

# Initialize FastAPI app
app = FastAPI()

# Load model and processor
#device = torch.device('cpu') # If youre running with no gpu uncomment and comment next line
device = torch.device('cuda:0')
processor = DonutProcessor.from_pretrained("ProjectsbyGaurav/donut-base-gaurav-receipt-epoch-5")
model = VisionEncoderDecoderModel.from_pretrained("ProjectsbyGaurav/donut-base-gaurav-receipt-epoch-5")
model.to(device)

# Configure Gemini API
genai.configure(api_key="GEMINI_KEY_GOES_HERE_PLEASE_REPLACE")  # Replace with your API key
gemini_model = genai.GenerativeModel("gemini-1.5-pro")

# Define the response schema using TypedDict
class ItemsForRemovalResponse(typing.TypedDict):
    items: list[str]  # List of items that may be removed

def load_and_preprocess_image(image_data: bytes, processor):
    """
    Load an image from bytes and preprocess it for the model.
    """
    image = Image.open(io.BytesIO(image_data)).convert("RGB")
    pixel_values = processor(image, return_tensors="pt").pixel_values
    return pixel_values

def generate_text_from_image(model, image_data: bytes, processor, device):
    """
    Generate text from an image using the trained model.
    """
    # Load and preprocess the image from bytes
    pixel_values = load_and_preprocess_image(image_data, processor)
    pixel_values = pixel_values.to(device)

    # Generate output using model
    model.eval()
    with torch.no_grad():
        task_prompt = "<s_receipt>"
        decoder_input_ids = processor.tokenizer(task_prompt, add_special_tokens=False, return_tensors="pt").input_ids
        decoder_input_ids = decoder_input_ids.to(device)
        generated_outputs = model.generate(
            pixel_values,
            decoder_input_ids=decoder_input_ids,
            max_length=model.decoder.config.max_position_embeddings,
            pad_token_id=processor.tokenizer.pad_token_id,
            eos_token_id=processor.tokenizer.eos_token_id,
            early_stopping=True,
            bad_words_ids=[[processor.tokenizer.unk_token_id]],
            return_dict_in_generate=True
        )

    # Decode generated output
    decoded_text = processor.batch_decode(generated_outputs.sequences)[0]
    decoded_text = decoded_text.replace(processor.tokenizer.eos_token, "").replace(processor.tokenizer.pad_token, "")
    decoded_text = re.sub(r"<.*?>", "", decoded_text, count=1).strip()  # remove first task start token
    decoded_text = processor.token2json(decoded_text)
    print(decoded_text)
    return decoded_text

def get_items_for_removal(purchase_data: str, reference_list: str):
    """
    Send the extracted purchase data and reference list to Gemini API to infer items for removal.
    
    Args:
        purchase_data (str): The extracted purchase data as a string from the receipt.
        reference_list (str): The reference shopping list as a comma-separated string of item names.
    
    Returns:
        list: A list of items to be removed based on the response from Gemini API.
    """
    prompt = f"""
    You are a shopping list analyzer. Respond with a JSON list like {{'items': list[str]}} of 'items' which may be removed from the list since they have now been purchased based on the following purchase data (extracted from a receipt).
    You are also provided with a reference shopping list: {reference_list}
    The names of items must be inferred from the purchase data and the reference list. Only return items you are confident are in the purchase data. Here is the purchase data:\n{purchase_data}
    """

    # Call the Gemini API for content generation
    result = gemini_model.generate_content(
        prompt,
        generation_config=genai.GenerationConfig(
            response_mime_type="application/json", 
            response_schema=list[ItemsForRemovalResponse]  # Use the TypedDict schema for the response
        ),
    )

    # Parse the response and return the "items" list
    try:
        items_to_remove = json.loads(result.text)
        
        # Check if items_to_remove is a non-empty list and contains "items" key
        if items_to_remove and "items" in items_to_remove[0]:
            return items_to_remove[0]["items"]
        else:
            print('NO ITEMS IN LIST')
            return []  # Return an empty list if "items" key is not found
    except (json.JSONDecodeError, KeyError, IndexError) as e:
        print(f"Error parsing response: {e}")
        return {"error": "Failed to parse response from Gemini API."}


@app.post("/process-images/")
async def process_images(files: List[UploadFile] = File(...), list: str = Form(...)):
    """
    Endpoint to process one or more images and return only the items_for_removal as a JSON array.
    The 'list' form parameter is a comma-separated list of item names to be used as a reference shopping list.
    
    Args:
        files (List[UploadFile]): List of image files.
        list (str): A comma-separated list of item names to be used as a reference shopping list.

    Returns:
        JSONResponse: A JSON object with a key `items_for_removal` containing the list of items to be removed.
    """
    all_items_for_removal = []
    
    for file in files:
        # Read the file content as bytes
        image_data = await file.read()
        
        # Generate text from the image
        extracted_text = generate_text_from_image(model, image_data, processor, device)
        
        # Get the items to be removed based on the extracted data and the provided reference list
        items_for_removal = get_items_for_removal(extracted_text, list)
        
        # Append the list of items for removal (only the "items" list) to the response array
        all_items_for_removal.extend(items_for_removal)
    
    # Return a JSON object with a key `items_for_removal` containing the list of items
    print(all_items_for_removal)
    return JSONResponse(content={"items_for_removal": all_items_for_removal})