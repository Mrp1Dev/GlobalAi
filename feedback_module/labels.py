"""Label taxonomy for the feedback classifier.

8 aspects x 2 polarities = 16 labels, plus 2 flags (no polarity).
The English descriptions are used by the zero-training baseline and
can be edited freely. Keep keys identical to recommendations.json.
"""

LABEL_DESCRIPTIONS = {
    "guide_hospitality:pos": "Guests praise the friendly, welcoming, helpful guide or host.",
    "guide_hospitality:neg": "Guests complain the guide or host was rude, unwelcoming, unhelpful or unfriendly.",
    "experience_activity:pos": "Guests enjoyed the activities, the farm walk and the overall experience.",
    "experience_activity:neg": "Guests found the activities boring, too long, too tiring or disappointing.",
    "food_drink:pos": "Guests liked the food, drinks, tasting or fresh farm produce.",
    "food_drink:neg": "Guests complained the food or drink was bad, cold, bland or unsafe.",
    "learning_authenticity:pos": "Guests learned a lot and felt the farm experience was authentic and genuine.",
    "learning_authenticity:neg": "Guests learned little or felt the experience was staged, fake or not authentic.",
    "price_value:pos": "Guests felt the price was fair and the tour was good value for money.",
    "price_value:neg": "Guests felt the tour was overpriced, expensive or poor value for money.",
    "timing_waiting:pos": "The tour started on time and was well organised.",
    "timing_waiting:neg": "Guests had to wait a long time, or the tour started late or was disorganised.",
    "directions_access:pos": "The farm was easy to find and reach.",
    "directions_access:neg": "The farm was hard to find, the directions were unclear or the road was difficult.",
    "facilities_cleanliness:pos": "The place was clean and the facilities such as toilets and seating were good.",
    "facilities_cleanliness:neg": "The place was dirty or the facilities such as toilets and seating were poor.",
    "would_recommend_or_return": "Guests say they would recommend the tour to others or come back again.",
    "wish_or_suggestion": "Guests wish for or suggest something extra, such as more activities or a new offering.",
}

LABELS = list(LABEL_DESCRIPTIONS.keys())
SEVERITIES = ["low", "medium", "high"]
